/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.sync.importer.BlazeKotlinWorkspaceImporter;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinImportResult;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinSyncData;
import com.google.idea.sdkcompat.kotlin.CommonCompilerArgumentsCompatUtils;
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
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.KotlinPluginUtil;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;

/** Supports Kotlin. */
public class BlazeKotlinSyncPlugin implements BlazeSyncPlugin {
  private static final String KOTLIN_PLUGIN_ID = KotlinPluginUtil.KOTLIN_PLUGIN_ID.getIdString();

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (Blaze.defaultBuildSystem().equals(BuildSystem.Bazel)
        && workspaceType.equals(WorkspaceType.JAVA)) {
      return ImmutableSet.of(LanguageClass.KOTLIN);
    }
    return ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.KOTLIN)
        ? ImmutableList.of(KOTLIN_PLUGIN_ID)
        : ImmutableList.of();
  }

  @Override
  public Collection<SectionParser> getSections() {
    return BlazeKotlinLanguageVersionSection.PARSERS;
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
    if (PluginUtils.isPluginInstalled(KOTLIN_PLUGIN_ID)) {
      maybeUpdateCompilerConfig(project, projectViewSet);
    }
  }

  /**
   * Update the compiler settings of the project if needed. The language setting applies to both the
   * api version and the language version. Blanket setting this project wide is fine. The rules
   * should catch incorrect usage.
   */
  private static void maybeUpdateCompilerConfig(Project project, ProjectViewSet projectViewSet) {
    LanguageVersion languageLevel =
        BlazeKotlinLanguageVersionSection.getLanguageLevel(projectViewSet);
    String languageLevelVersionString = languageLevel.getVersionString();
    CommonCompilerArguments settings =
        CommonCompilerArgumentsCompatUtils.getUnfrozenSettings(project);
    boolean updated = false;
    String apiVersion = CommonCompilerArgumentsCompatUtils.getApiVersion(settings);
    String languageVersion = CommonCompilerArgumentsCompatUtils.getLanguageVersion(settings);
    if (apiVersion == null || !apiVersion.equals(languageLevelVersionString)) {
      updated = true;
      CommonCompilerArgumentsCompatUtils.setApiVersion(settings, languageLevelVersionString);
    }
    if (languageVersion == null || !languageVersion.equals(languageLevelVersionString)) {
      updated = true;
      CommonCompilerArgumentsCompatUtils.setLanguageVersion(settings, languageLevelVersionString);
    }
    if (updated) {
      KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).setSettings(settings);
    }
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
        new BlazeKotlinWorkspaceImporter(project, workspaceRoot, projectViewSet, targetMap);
    BlazeKotlinImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(
                  new TimingScope("KotlinWorkspaceImporter", TimingScope.EventType.Other));
              return blazeKotlinWorkspaceImporter.importWorkspace();
            });
    BlazeKotlinSyncData syncData = new BlazeKotlinSyncData(importResult);
    syncStateBuilder.put(BlazeKotlinSyncData.class, syncData);
  }

  /**
   * Attaches sources to Kotlin external artifacts, add missing mandatory std libraries and perform
   * last bit of Kotlin configuration.
   */
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
    LibraryTable.ModifiableModel libraryTable =
        ProjectLibraryTable.getInstance(project).getModifiableModel();
    externalKotlinLibraries(blazeProjectData)
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

  private Stream<BlazeJarLibrary> externalKotlinLibraries(BlazeProjectData blazeProjectData) {
    ImmutableMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetToLibraryMap =
        BlazeKotlinSyncData.get(blazeProjectData).importResult.kotlinTargetToLibraryMap;
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
}
