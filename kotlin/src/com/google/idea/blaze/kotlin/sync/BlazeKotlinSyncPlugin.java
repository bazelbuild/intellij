/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.BlazeKotlin;
import com.google.idea.blaze.kotlin.sync.importer.BlazeKotlinWorkspaceImporter;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinImportResult;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinSyncData;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinToolchainIdeInfo;
import com.google.idea.sdkcompat.kotlin.BlazeKotlinCompilerArgumentsUpdaterCompat;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static com.google.idea.blaze.kotlin.BlazeKotlin.COMPILER_WORKSPACE_NAME;

public class BlazeKotlinSyncPlugin implements BlazeSyncPlugin {
  private static boolean kotlinRepoAbsentFromWorkspace(Project project) {
    WorkspaceRoot workspaceRoot =
        WorkspaceHelper.resolveExternalWorkspace(project, COMPILER_WORKSPACE_NAME);
    return workspaceRoot == null || !workspaceRoot.directory().exists();
  }

  private static void maybeAttachSourceJars(
      ArtifactLocationDecoder artifactLocationDecoder,
      BlazeJarLibrary lib,
      Library.ModifiableModel modifiableIjLibrary) {
    if (modifiableIjLibrary.getFiles(OrderRootType.SOURCES).length == 0) {
      for (ArtifactLocation sourceJar : lib.libraryArtifact.sourceJars) {
        File srcJarFile = artifactLocationDecoder.decode(sourceJar);
        VirtualFile vfSourceJar = VfsUtil.findFileByIoFile(srcJarFile, false);
        if (vfSourceJar != null) {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vfSourceJar);
          if (jarRoot != null) {
            modifiableIjLibrary.addRoot(jarRoot, OrderRootType.SOURCES);
          }
        }
      }
    }
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return Blaze.getBuildSystem(null) == Blaze.BuildSystem.Bazel
        ? ImmutableSet.of(LanguageClass.KOTLIN)
        : ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.KOTLIN)
        ? ImmutableList.of(BlazeKotlin.PLUGIN_ID)
        : ImmutableList.of();
  }

  @Override
  public Collection<SectionParser> getSections() {
    return BlazeKotlinSections.PARSERS;
  }

  /**
   * Ensure the plugin is enabled and that the language version is as intended. The actual
   * installation of the plugin should primarily be handled by {@link AlwaysPresentKotlinSyncPlugin}
   */
  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
      return;
    }
    if (PluginUtils.isPluginInstalled(BlazeKotlin.PLUGIN_ID)) {
      PluginUtils.installOrEnablePlugin(BlazeKotlin.PLUGIN_ID);
    }
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
      return true;
    }
    if (kotlinRepoAbsentFromWorkspace(project)) {
      IssueOutput.warn(BlazeKotlin.Issues.RULES_ABSENT_FROM_WORKSPACE).submit(context);
      return false;
    }
    return true;
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, final BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
      return null;
    }
    return new LibrarySource.Adapter() {
      @Override
      public List<? extends BlazeLibrary> getLibraries() {
        return BlazeKotlinSyncData.get(blazeProjectData).importResult.libraries;
      }
    };
  }

  @Override
  public void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeInfo blazeInfo,
      @Nullable WorkingSet workingSet,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
      return;
    }
    BlazeKotlinWorkspaceImporter blazeKotlinWorkspaceImporter =
        new BlazeKotlinWorkspaceImporter(
            project, workspaceRoot, projectViewSet, targetMap, context, artifactLocationDecoder);
    BlazeKotlinImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(
                  new TimingScope("KotlinWorkspaceImporter", TimingScope.EventType.Other));
              BlazeKotlinImportResult result = blazeKotlinWorkspaceImporter.importWorkspace();
              syncKotlinProjectSettings(project, projectViewSet, result.toolchainIdeInfo);
              return result;
            });
    BlazeKotlinSyncData syncData = new BlazeKotlinSyncData(importResult);
    syncStateBuilder.put(BlazeKotlinSyncData.class, syncData);
  }

  private void syncKotlinProjectSettings(
      Project project,
      ProjectViewSet projectViewSet,
      @Nullable BlazeKotlinToolchainIdeInfo toolchainIdeInfo) {
    if (toolchainIdeInfo == null) {
      IssueOutput.issue(
          IssueOutput.Category.WARNING,
          BlazeKotlin.Issues.UPDATE_RULES_WARNING.apply(
              "The current Kotlin rules do not use toolchains"));
    }

    BlazeKotlinCompilerArgumentsUpdaterCompat argumentsUpdater =
        BlazeKotlinCompilerArgumentsUpdaterCompat.build(project);
    Optional<LanguageVersion> languageLevelFromViewSet =
        BlazeKotlinSections.getLanguageLevel(projectViewSet);

    if (toolchainIdeInfo != null) {
      if (languageLevelFromViewSet.isPresent()) {
        IssueOutput.issue(
            IssueOutput.Category.INFORMATION, BlazeKotlin.Issues.LANGUAGE_VERSION_SECTION_IGNORED);
      }
      argumentsUpdater.updateLanguageVersion(toolchainIdeInfo.common.languageVersion);
      argumentsUpdater.updateApiVersion(toolchainIdeInfo.common.apiVersion);
      argumentsUpdater.updateCoroutineState(toolchainIdeInfo.common.coroutines);
      argumentsUpdater.updateJvmTarget(toolchainIdeInfo.jvm.jvmTarget);
    } else {
      LanguageVersion languageVersion =
          languageLevelFromViewSet.orElse(BlazeKotlin.DEFAULT_LANGUAGE_VERSION);
      argumentsUpdater.updateLanguageVersion(languageVersion.getVersionString());
      argumentsUpdater.updateApiVersion(languageVersion.getVersionString());
    }
    argumentsUpdater.commit();
  }

  /**
   * Attaches sources to Kotlin external artifacts, add missing mandatory std libraries and perform
   * last bit of Kotlin configuration.
   */
  // Most of library table manipulation goes away (source attaching) once the bazel java_common
  // infrastructure leaves kotlin ijars intact. The kotlin rules
  // will then use java_import instead of kt_jvm_import it will also then be a good time to get rid
  // of kotlin_stdlib. We probably still need to attach the
  // kotlin stdlibs manually here as they are not discoverable via aspect processing (they are added
  // implicitly inside the rule implementation).
  // see: https://github.com/bazelbuild/bazel/issues/4549
  // the issue in the rule repo is: https://github.com/bazelbuild/rules_kotlin/issues/4
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
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
      return;
    }
    // validated in the validate method, so an error shouldn't be raised here.
    if (kotlinRepoAbsentFromWorkspace(project)) {
      return;
    }
    BlazeKotlinSyncData syncData = BlazeKotlinSyncData.get(blazeProjectData);
    LibraryTable.ModifiableModel libraryTable =
        ProjectLibraryTable.getInstance(project).getModifiableModel();
    externalKotlinLibraries(syncData)
        .forEach(
            lib -> {
              Library library = libraryTable.getLibraryByName(lib.key.getIntelliJLibraryName());
              if (library == null) {
                library = libraryTable.createLibrary(lib.key.getIntelliJLibraryName());
              }
              Library.ModifiableModel modifiableIjLibrary = library.getModifiableModel();
              maybeAttachSourceJars(
                  blazeProjectData.artifactLocationDecoder, lib, modifiableIjLibrary);
              modifiableIjLibrary.commit();
            });
    libraryTable.commit();
    KotlinJavaModuleConfigurator.Companion.getInstance().configureSilently(project);
  }

  @NotNull
  private Stream<BlazeJarLibrary> externalKotlinLibraries(BlazeKotlinSyncData syncData) {
    ImmutableMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetToLibraryMap =
        syncData.importResult.kotlinTargetToLibraryMap;
    Map<String, BlazeJarLibrary> tally = new HashMap<>();
    targetToLibraryMap.forEach(
        (ideInfo, libraries) -> {
          if (ideInfo.kind.isOneOf(Kind.KT_JVM_IMPORT, Kind.KOTLIN_STDLIB)) {
            libraries.forEach(lib -> tally.putIfAbsent(lib.key.getIntelliJLibraryName(), lib));
          }
        });
    BlazeKotlinStdLib.prepareBlazeLibraries(BlazeKotlinStdLib.MANDATORY_STDLIBS)
        .forEach(lib -> tally.putIfAbsent(lib.key.getIntelliJLibraryName(), lib));
    return tally.values().stream();
  }
}
