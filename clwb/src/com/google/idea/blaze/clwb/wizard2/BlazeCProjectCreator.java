/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.wizard2;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;

class BlazeCProjectCreator {
  private static final Logger LOG = Logger.getInstance(BlazeCProjectCreator.class);

  private final WizardContext wizardContext;
  private final ProjectBuilder projectBuilder;

  public BlazeCProjectCreator(WizardContext wizardContext, ProjectBuilder projectBuilder) {
    this.wizardContext = wizardContext;
    this.projectBuilder = projectBuilder;
  }

  @Nullable
  public Project createFromWizard() {
    try {
      return doCreate();
    } catch (final IOException e) {
      UIUtil.invokeLaterIfNeeded(
          () -> Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed"));
      return null;
    }
  }

  @Nullable
  private Project doCreate() throws IOException {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = wizardContext.getProjectFileDirectory();

    try {
      File projectDir = new File(projectFilePath).getParentFile();
      LOG.assertTrue(
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
        return null;
      }

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        newProject.save();
      }

      if (!projectBuilder.validate(null, newProject)) {
        return null;
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

      if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
        IdeFocusManager instance = IdeFocusManager.findInstance();
        IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
        if (lastFocusedFrame instanceof IdeFrameEx) {
          boolean fullScreen = ((IdeFrameEx) lastFocusedFrame).isInFullScreen();
          if (fullScreen) {
            newProject.putUserData(IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN, Boolean.TRUE);
          }
        }
      }

      projectManager.openProject(newProject);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        newProject.save();
      }
      return newProject;
    } finally {
      projectBuilder.cleanup();
    }
  }
}
