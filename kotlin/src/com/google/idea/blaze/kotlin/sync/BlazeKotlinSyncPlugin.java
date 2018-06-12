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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.KotlinToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.sdkcompat.kotlin.CommonCompilerArgumentsCompatUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;

/** Supports Kotlin. */
public class BlazeKotlinSyncPlugin implements BlazeSyncPlugin {
  // we don't get the plugin ID from org.jetbrains.kotlin.idea.KotlinPluginUtil because that
  // requires some integration testing setup (e.g. will throw an exception if idea.home.path isn't
  // set).
  private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin";
  private static final LanguageVersion DEFAULT_VERSION = LanguageVersion.KOTLIN_1_2;

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return KotlinUtils.isKotlinSupportEnabled(workspaceType)
        ? ImmutableSet.of(LanguageClass.KOTLIN)
        : ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.KOTLIN)
        ? ImmutableList.of(KOTLIN_PLUGIN_ID)
        : ImmutableList.of();
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
    updateProjectSettings(project, blazeProjectData);
  }

  /**
   * Update the compiler settings of the project if needed. The language setting applies to both the
   * api version and the language version. Blanket setting this project wide is fine. The rules
   * should catch incorrect usage.
   */
  private static void updateProjectSettings(Project project, BlazeProjectData blazeProjectData) {
    LanguageVersion languageLevel = getLanguageVersion(findToolchain(blazeProjectData.targetMap));
    String versionString = languageLevel.getVersionString();
    CommonCompilerArguments settings =
        CommonCompilerArgumentsCompatUtils.getUnfrozenSettings(project);
    boolean updated = false;
    String apiVersion = CommonCompilerArgumentsCompatUtils.getApiVersion(settings);
    String languageVersion = CommonCompilerArgumentsCompatUtils.getLanguageVersion(settings);
    if (apiVersion == null || !apiVersion.equals(versionString)) {
      updated = true;
      CommonCompilerArgumentsCompatUtils.setApiVersion(settings, versionString);
    }
    if (languageVersion == null || !languageVersion.equals(versionString)) {
      updated = true;
      CommonCompilerArgumentsCompatUtils.setLanguageVersion(settings, versionString);
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
        return findKotlinSdkLibraries(blazeProjectData);
      }
    };
  }

  private static LanguageVersion getLanguageVersion(@Nullable KotlinToolchainIdeInfo toolchain) {
    if (toolchain == null) {
      return DEFAULT_VERSION;
    }
    LanguageVersion version = LanguageVersion.fromVersionString(toolchain.languageVersion);
    return version != null ? version : DEFAULT_VERSION;
  }

  private static List<BlazeJarLibrary> findKotlinSdkLibraries(BlazeProjectData blazeProjectData) {
    KotlinToolchainIdeInfo toolchain = findToolchain(blazeProjectData.targetMap);
    if (toolchain == null) {
      return ImmutableList.of();
    }
    List<BlazeJarLibrary> libraries = new ArrayList<>();
    for (Label label : toolchain.sdkTargets) {
      TargetIdeInfo target = blazeProjectData.targetMap.get(TargetKey.forPlainTarget(label));
      if (target == null || target.javaIdeInfo == null) {
        continue;
      }
      libraries.addAll(
          target.javaIdeInfo.jars.stream().map(BlazeJarLibrary::new).collect(Collectors.toList()));
    }
    return libraries;
  }

  @Nullable
  private static KotlinToolchainIdeInfo findToolchain(TargetMap targets) {
    return targets
        .targets()
        .stream()
        .map(t -> t.kotlinToolchainIdeInfo)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  static class Listener extends SyncListener.Adapter {
    @Override
    public void afterSync(
        Project project, BlazeContext context, SyncMode syncMode, SyncResult syncResult) {
      ApplicationManager.getApplication()
          .invokeLater(
              () ->
                  KotlinJavaModuleConfigurator.Companion.getInstance().configureSilently(project));
    }
  }
}
