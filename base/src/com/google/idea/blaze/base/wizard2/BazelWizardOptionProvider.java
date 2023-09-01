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
package com.google.idea.blaze.base.wizard2;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.project.AutoImportProjectOpenProcessor;
import com.intellij.openapi.Disposable;

import java.io.File;

/** Provides bazel options for the wizard. */
public class BazelWizardOptionProvider implements BlazeWizardOptionProvider {

  @Override
  public ImmutableList<TopLevelSelectWorkspaceOption> getSelectWorkspaceOptions(
      BlazeNewProjectBuilder builder, Disposable parentDisposable) {
    return ImmutableList.of(new UseExistingBazelWorkspaceOption(builder));
  }

  @Override
  public ImmutableList<BlazeSelectProjectViewOption> getSelectProjectViewOptions(
      BlazeNewProjectBuilder builder) {
    ImmutableList.Builder<BlazeSelectProjectViewOption> options = new ImmutableList.Builder<>();

    options.add(new CreateFromScratchProjectViewOption());
    options.add(new ImportFromWorkspaceProjectViewOption(builder));
    options.add(new GenerateFromBuildFileSelectProjectViewOption(builder));
    options.add(new CopyExternalProjectViewOption(builder));

    String projectViewFromEnv = System.getenv(AutoImportProjectOpenProcessor.PROJECT_VIEW_FROM_ENV);
    WorkspaceRoot workspaceRoot = builder.getWorkspaceData() != null ? builder.getWorkspaceData().workspaceRoot() : null;

    if (workspaceRoot != null) {
      if (projectViewFromEnv != null) {
        File projectViewFromEnvFile = new File(projectViewFromEnv);
        if (projectViewFromEnvFile.exists()) {
          options.add(UseKnownProjectViewOption.fromEnvironmentVariable(workspaceRoot, projectViewFromEnvFile));
        }
      }
      if (workspaceRoot.absolutePathFor(AutoImportProjectOpenProcessor.MANAGED_PROJECT_RELATIVE_PATH).toFile().exists()) {
        options.add(UseKnownProjectViewOption.fromManagedProject(workspaceRoot));
      }
    }

    return options.build();
  }
}
