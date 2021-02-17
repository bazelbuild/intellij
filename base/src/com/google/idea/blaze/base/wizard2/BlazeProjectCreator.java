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

import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

class BlazeProjectCreator {
  private static final Logger logger = Logger.getInstance(BlazeProjectCreator.class);

  private final WizardContext wizardContext;
  private final ProjectBuilder projectBuilder;

  BlazeProjectCreator(WizardContext wizardContext, ProjectBuilder projectBuilder) {
    this.wizardContext = wizardContext;
    this.projectBuilder = projectBuilder;
  }

  void createFromWizard() {
    try {
      doCreate();
    } catch (final IOException e) {
      UIUtil.invokeLaterIfNeeded(
          () -> Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed"));
    }
  }

  private void doCreate() throws IOException {
    String projectFilePath = wizardContext.getProjectFileDirectory();

    File projectDir = new File(projectFilePath).getParentFile();
    logger.assertTrue(
        projectDir != null,
        "Cannot create project in '" + projectFilePath + "': no parent file exists");
    FileUtil.ensureExists(projectDir);
    if (wizardContext.getProjectStorageFormat() == StorageScheme.DIRECTORY_BASED) {
      final File ideaDir = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
      FileUtil.ensureExists(ideaDir);
    }

    String name = wizardContext.getProjectName();
    Project newProject = projectBuilder.createProject(name, projectFilePath);
    if (newProject == null) {
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    if (!projectBuilder.validate(null, newProject)) {
      return;
    }

    projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);

    StartupManager.getInstance(newProject)
        .registerPostStartupActivity(
            () -> {
              // ensure the dialog is shown after all startup activities are done
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(
                  () -> {
                    if (newProject.isDisposed()
                        || ApplicationManager.getApplication().isUnitTestMode()) {
                      return;
                    }
                    ApplicationManager.getApplication()
                        .invokeLater(
                            () -> {
                              if (newProject.isDisposed()) {
                                return;
                              }
                              final ToolWindow toolWindow =
                                  ToolWindowManager.getInstance(newProject)
                                      .getToolWindow(ToolWindowId.PROJECT_VIEW);
                              if (toolWindow != null) {
                                toolWindow.activate(null);
                              }
                            },
                            ModalityState.NON_MODAL);
                  });
            });

    ProjectUtil.updateLastProjectLocation(projectFilePath);

    BaseSdkCompat.openProject(newProject, Paths.get(projectFilePath));

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      SaveAndSyncHandler.getInstance().scheduleProjectSave(newProject);
    }
  }
}
