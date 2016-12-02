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
package com.google.idea.blaze.java.wizard2;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import icons.BlazeIcons;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/** Wrapper around a {@link BlazeNewProjectBuilder} to fit into IntelliJ's import framework. */
class BlazeProjectImportBuilder extends ProjectImportBuilder<Void> {
  private BlazeNewProjectBuilder builder = new BlazeNewProjectBuilder();

  @NotNull
  @Override
  public String getName() {
    return Blaze.defaultBuildSystemName();
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
  public List<Void> getList() {
    return ImmutableList.of();
  }

  @Override
  public boolean isMarked(Void element) {
    return true;
  }

  @Override
  public void setList(List<Void> gradleProjects) {}

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {}

  @Override
  public List<Module> commit(
      final Project project,
      ModifiableModuleModel model,
      ModulesProvider modulesProvider,
      ModifiableArtifactModel artifactModel) {
    builder.commitToProject(project);
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE = false;
    return ImmutableList.of();
  }

  public BlazeNewProjectBuilder builder() {
    return builder;
  }
}
