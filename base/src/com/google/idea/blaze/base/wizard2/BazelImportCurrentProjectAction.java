/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.bazel.BazelWorkspaceRootProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public class BazelImportCurrentProjectAction extends AnAction {

  final File workspaceRootFile;

  public BazelImportCurrentProjectAction(File workspaceRootFile) {
    super("Import Bazel project");
    this.workspaceRootFile = workspaceRootFile;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    BlazeNewProjectWizard wizard = new BlazeNewProjectWizard() {
      protected void init() {
        BazelWorkspaceRootProvider.INSTANCE.isWorkspaceRoot(workspaceRootFile);
        WorkspaceRoot root = new WorkspaceRoot(workspaceRootFile);

        WorkspacePathResolver pathResolver = new WorkspacePathResolverImpl(root);
        builder.builder().setWorkspaceData(
            WorkspaceTypeData.builder()
                .setWorkspaceName(workspaceRootFile.getName())
                .setWorkspaceRoot(root)
                .setCanonicalProjectDataLocation(workspaceRootFile)
                .setFileBrowserRoot(workspaceRootFile)
                .setWorkspacePathResolver(pathResolver)
                .setBuildSystem(BuildSystemName.Bazel)
                .build()
        );

        super.init();
      }

      @Override
      protected ProjectImportWizardStep[] getSteps(WizardContext context) {
        return new ProjectImportWizardStep[]{
            new BlazeSelectProjectViewImportWizardStep(context),
            new BlazeEditProjectViewImportWizardStep(context)
        };
      }
    };

    if (!wizard.showAndGet()) {
      return;
    }

    BlazeProjectCreator projectCreator = new BlazeProjectCreator(wizard.builder);
    BlazeImportProjectAction.createFromWizard(projectCreator, wizard.context);
  }
}
