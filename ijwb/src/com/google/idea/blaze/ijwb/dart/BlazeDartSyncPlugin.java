/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ijwb.dart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.sdkcompat.dart.DartSdkCompatUtils;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports dart. */
public class BlazeDartSyncPlugin implements BlazeSyncPlugin {

  private static final String DART_PLUGIN_ID = "Dart";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.DART);
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.DART)
        ? ImmutableList.of(DART_PLUGIN_ID)
        : ImmutableList.of();
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
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.DART)) {
      return;
    }

    Library dartSdkLibrary = DartSdkCompatUtils.findDartLibrary(project);
    if (dartSdkLibrary != null) {
      if (workspaceModifiableModel.findLibraryOrderEntry(dartSdkLibrary) == null) {
        workspaceModifiableModel.addLibraryEntry(dartSdkLibrary);
      }
    } else {
      IssueOutput.error(
              "Dart language support is requested, but the Dart SDK was not found. "
                  + "You must manually enable Dart support from "
                  + "File > Settings > Languages & Frameworks > Dart.")
          .submit(context);
    }
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.DART)) {
      return true;
    }
    if (!PluginUtils.isPluginEnabled(DART_PLUGIN_ID)) {
      IssueOutput.error(
              "The Dart plugin is required for Dart support. "
                  + "Click here to install/enable the plugin and restart")
          .navigatable(PluginUtils.installOrEnablePluginNavigable(DART_PLUGIN_ID))
          .submit(context);
      return false;
    }
    return true;
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.DART)) {
      return null;
    }
    return new BlazeDartLibrarySource();
  }
}
