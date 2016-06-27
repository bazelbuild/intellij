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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.ijwb.ide.IdeCheck;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Supports dart.
 */
public class BlazeDartSyncPlugin extends BlazeSyncPlugin.Adapter {

  static final String DART_SDK_LIBRARY_NAME = "Dart SDK";
  private static final String DART_PLUGIN_ID = "Dart";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.DART);
  }

  @Override
  public void updateProjectStructure(Project project,
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

    Library dartSdkLibrary = ApplicationLibraryTable.getApplicationTable().getLibraryByName(DART_SDK_LIBRARY_NAME);
    if (dartSdkLibrary != null) {
      if (workspaceModifiableModel.findLibraryOrderEntry(dartSdkLibrary) == null) {
        workspaceModifiableModel.addLibraryEntry(dartSdkLibrary);
      }
    } else {
      IssueOutput
        .error("Dart language support is requested, but the Dart SDK was not found. "
               + "You must manually enable Dart support from File > Settings > Languages & Frameworks > Dart.")
        .submit(context);
    }
  }

  @Override
  public boolean validateProjectView(BlazeContext context,
                                     ProjectViewSet projectViewSet,
                                     WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.DART)) {
      return true;
    }
    if (!IdeCheck.isPluginEnabled(DART_PLUGIN_ID)) {
      IssueOutput.error("Dart plugin needed for Dart language support.").submit(context);
      return false;
    }
    return true;
  }
}
