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
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.scala.sync.importer.BlazeScalaWorkspaceImporter;
import com.google.idea.blaze.scala.sync.model.BlazeScalaImportResult;
import com.google.idea.blaze.scala.sync.model.BlazeScalaSyncData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;

import java.io.File;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import org.jetbrains.plugins.scala.project.ScalaLibraryProperties;
import org.jetbrains.plugins.scala.project.ScalaLibraryType;
import scala.Option;
import scala.collection.immutable.List$;

/** Supports scala. */
public class BlazeScalaSyncPlugin implements BlazeSyncPlugin {
  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType.equals(WorkspaceType.JAVA)) {
      return ImmutableSet.of(LanguageClass.SCALA);
    }
    return ImmutableSet.of();
  }

  /**
   * See <a href="https://github.com/JetBrains/intellij-scala/blob/4f89d1dd8f71c36a5bc77a90d3722afb49a10822/scala/scala-impl/src/org/jetbrains/plugins/scala/project/package.scala#L81-L83">...</a>
   */
  private static final Pattern LIBRARY_VERSION = Pattern.compile("(?<=[:\\-])\\d+\\.\\d+\\.\\d+[^:\\s]*");

  private static final Pattern RUNTIME_LIBRARY = Pattern.compile("((?:scala|dotty|scala3|org_scala_lang_scala)(?:_|-)library).+");

  private static boolean isRuntimeLibrary(String name) {
    return RUNTIME_LIBRARY.matcher(name).find();
  }

  private static Option<String> libraryVersion(String name) {
    Matcher matcher = LIBRARY_VERSION.matcher(name);
    if (matcher.find()) {
      return Option.<String>apply(matcher.group());
    } else {
      return Option.<String>empty();
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
    for (Library library :
        LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries()) {
      // Configure metadata for the Scala library to inform the Scala plugin which SDK and version to use
      // TODO: If there are multiple Scala library versions on the classpath, this will select a random one.
      // TODO: We could pick the min or max version, but either choice is problematic, see SCL-18866.
      // TODO: The best solution would be letting the user specify the expected Scala version explicitly
      if (library.getName() != null && isRuntimeLibrary(library.getName())) {
        ScalaLibraryProperties properties = ScalaLibraryProperties.apply(libraryVersion(library.getName()), List$.MODULE$.<File>empty(), List$.MODULE$.<File>empty());
        ExistingLibraryEditor editor = new ExistingLibraryEditor(library, null);
        editor.setType(ScalaLibraryType.apply());
        editor.setProperties(properties);
        editor.commit();
        return;
      }
    }
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
