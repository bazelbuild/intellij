/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.scala.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.scala.sync.importer.BlazeScalaWorkspaceImporter;
import com.google.idea.blaze.scala.sync.model.BlazeScalaImportResult;
import com.google.idea.blaze.scala.sync.model.BlazeScalaSyncData;
import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.project.ScalaLibraryProperties;
import org.jetbrains.plugins.scala.project.ScalaLibraryType;
import scala.*;
import scala.collection.immutable.Seq$;
import scala.jdk.javaapi.CollectionConverters;

/** Supports scala. */
public class BlazeScalaSyncPlugin implements BlazeSyncPlugin {
  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType.equals(WorkspaceType.JAVA)) {
      return ImmutableSet.of(LanguageClass.SCALA);
    }
    return ImmutableSet.of();
  }

  private final Pattern versionPattern = Pattern.compile("\\d+(?:\\.\\d+)+");

  private final class ScalaSdkJar {
    public String pattern;
    public boolean isScalaLibraryJar;
    public Library highestVersionLibrary;
    public String highestVersion;

    public ScalaSdkJar(String _pattern, boolean _isScalaLibraryJar) {
      this.pattern = _pattern;
      this.isScalaLibraryJar = _isScalaLibraryJar;
      this.highestVersionLibrary = null;
      this.highestVersion = "";
    }
  }

  @Override
  public void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.SCALA)) {
      return;
    }

    // This is all kinds of hacky. As mentioned in a comment below, these should be coming from
    // the toolchain. As written, this only picks up a Scala 2 or Scala 3 SDK. Comment one
    // block of dependencies out depending on which version you want supported. Again, super
    // hacky, but it works for our codebase right now, so let's just do this until we make this
    // come from the toolchain.
    ScalaSdkJar[] sdkClasses = {
      // Shared 2 & 3
      new ScalaSdkJar("^scala-library-.+", true),

      // Scala 2 support
      new ScalaSdkJar("^scala-reflect-.+", true),
      new ScalaSdkJar("^scala-compiler-.+", false)

      // Scala 3 support
      // new ScalaSdkJar("^scala3-library_3.+", true),
      // new ScalaSdkJar("^jna-.+", false),
      // new ScalaSdkJar("^jline-reader-.+", false),
      // new ScalaSdkJar("^jline-terminal-.+", false),
      // new ScalaSdkJar("^jline-terminal-.+", false),
      // new ScalaSdkJar("^scala-asm-.+", false),
      // new ScalaSdkJar("^scala3-compiler_3.+", false),
      // new ScalaSdkJar("^scala3-interfaces.+", false),
      // new ScalaSdkJar("^tasty-core_3.+", false),
      // new ScalaSdkJar("^compiler-interface.+", false),
      // new ScalaSdkJar("^util-interface-.+", false),
      // new ScalaSdkJar("^protobuf-java-.+", false)
    };

    // The point of the rest of this function is to identify and set up a Scala SDK
    // in IntelliJ. If you don't have an SDK set up, you get the missing SDK notification
    // and a handful of features don't work as well.
    //
    // This is done pretty similarly to how the Scala IntelliJ plugin sets up its
    // SDK. I couldn't find a good public entry point in the Scala IntelliJ plugin to use.
    // Some future work could be making some of the private functions in that plugin public,
    // so we can use them directly in this plugin.
    String highestScalaLibraryVersion = "";
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    for (Library library : libraryTable.getLibraries()) {
      String libraryName = library.getName();
      // rules_jvm_external started prepending "header_" to compile jars in order to modify the
      // jar manifest for third party libraries. This breaks the Scala IntelliJ plugin in some ways.
      // This gets around that in the Bazel IntelliJ plugin, but doesn't fix the places the Scala
      // IntelliJ plugin checks that jars start with scala-library or similar.
      String rulesJvmExternalPrefix = "header_";
      if (libraryName.startsWith(rulesJvmExternalPrefix)) {
        libraryName = libraryName.substring(rulesJvmExternalPrefix.length());
      }

      // Go find the highest version of all the Scala SDK deps in the project LibraryTable
      // This is not guaranteed to be the versions needed by the Scala toolchain.
      // This is just a quick hack. The Bazel IntelliJ Scala code needs to be modified
      // to support toolchains and then we can just grab all the dependencies for the
      // SDK from the toolchain.
      // We uniquely track the highest Scala library version because we name the sdk we
      // create using that version.
      if (libraryName != null) {
        for (ScalaSdkJar currentClass : sdkClasses) {
          if (libraryName.matches(currentClass.pattern)) {
            Matcher matcher = versionPattern.matcher(libraryName);
            String version = matcher.find() ? matcher.group() : null;
            if (version != null && version.compareTo(currentClass.highestVersion) > 0) {
              currentClass.highestVersion = version;
              currentClass.highestVersionLibrary = library;

              if (currentClass.isScalaLibraryJar && version.compareTo(highestScalaLibraryVersion) > 0) {
                highestScalaLibraryVersion = version;
              }
            }
          }
        }

      }
    }

    // Create the Scala SDK from the SDK deps we just found
    LinkedList<VirtualFile> libraryClasses = new LinkedList<VirtualFile>();
    LinkedList<File> compilerClasspath = new LinkedList<File>();
    for (ScalaSdkJar currentClass : sdkClasses) {
      if (currentClass.highestVersionLibrary != null) {
        VirtualFile[] currentClassVirtualFiles =
          currentClass.highestVersionLibrary.getFiles(OrderRootType.CLASSES);

        if (currentClass.isScalaLibraryJar) {
          Collections.addAll(libraryClasses, currentClassVirtualFiles);
        }
        for (VirtualFile currentVirtualFile : currentClassVirtualFiles) {
          String path = currentVirtualFile.getCanonicalPath();
          // Remove the !/ at the end of the canonical path to the jars. We want the path
          // to the jar file and don't care about anything inside the jar.
          String suffix = "!/";
          if (path != null && path.endsWith(suffix)) {
            path = path.substring(0, path.length() - suffix.length());
          }
          compilerClasspath.add(new File(path));
        }
      }
    }

    String sdkName = "scala-sdk-" + highestScalaLibraryVersion;
    // Need a final on this string because we use it in the lambda below
    final String scalaVersion = highestScalaLibraryVersion;
    ScalaLibraryProperties properties = ScalaLibraryProperties.apply(
      Some.apply(scalaVersion),
      CollectionConverters.asScala(compilerClasspath).toList(),
      Seq$.MODULE$.empty()
    );

    WriteAction.computeAndWait(() -> {
      Library library = libraryTable.createLibrary(sdkName);
      LibraryEx.ModifiableModelEx libraryModel =
        (LibraryEx.ModifiableModelEx) library.getModifiableModel();
      try {
        for (VirtualFile file : libraryClasses) {
          libraryModel.addRoot(file, OrderRootType.CLASSES);
        }
        libraryModel.addRoot(
            "https://www.scala-lang.org/api/" + scalaVersion + "/",
            JavadocOrderRootType.getInstance()
        );
        libraryModel.setKind(ScalaLibraryType.apply().getKind());
        libraryModel.setProperties(properties);
        libraryModel.commit();

        workspaceModifiableModel.addLibraryEntry(library);
        return null;
      } catch (Throwable t) {
        libraryModel.dispose();
        throw t;
      }
    });
  }

  @Override
  public void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeVersionData blazeVersionData,
      @Nullable WorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      SyncMode syncMode) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.SCALA)) {
      return;
    }
    BlazeScalaWorkspaceImporter blazeScalaWorkspaceImporter =
        new BlazeScalaWorkspaceImporter(project, workspaceRoot, projectViewSet, targetMap);
    BlazeScalaImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("ScalaWorkspaceImporter", EventType.Other));
              return blazeScalaWorkspaceImporter.importWorkspace();
            });
    syncStateBuilder.put(new BlazeScalaSyncData(importResult));
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.SCALA)) {
      return null;
    }
    return new BlazeScalaLibrarySource(blazeProjectData);
  }
}
