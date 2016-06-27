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
package com.google.idea.blaze.java.wizard;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.wizard.BlazeNewProjectBuilder;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Project builder for Blaze projects.
 */
public class BlazeNewJavaProjectImportBuilder extends ProjectImportBuilder<BlazeProjectData> {
  private BlazeImportSettings importSettings;
  private ProjectView projectView;

  @NotNull
  @Override
  public String getName() {
    return "Blaze";
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Blaze;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  @Override
  public List<BlazeProjectData> getList() {
    return ImmutableList.of();
  }

  @Override
  public boolean isMarked(BlazeProjectData element) {
    return true;
  }

  @Override
  public void setList(List<BlazeProjectData> gradleProjects) {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Override
  public List<Module> commit(final Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    assert importSettings != null;
    assert projectView != null;

    return BlazeNewProjectBuilder.commit(project, importSettings, projectView);
  }

  public void setImportSettings(BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
  }

  public void setProjectView(ProjectView projectView) {
    this.projectView = projectView;
  }
}
