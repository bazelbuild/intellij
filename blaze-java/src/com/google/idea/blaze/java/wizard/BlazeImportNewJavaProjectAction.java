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

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.wizard.BlazeImportFileChooser;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;

import javax.annotation.Nullable;
import java.io.IOException;


public class BlazeImportNewJavaProjectAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(BlazeImportNewJavaProjectAction.class);

  public BlazeImportNewJavaProjectAction() {
    super("Import Project...");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    // this importer only supports importing blaze projects
    if (!BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Blaze)) {
      e.getPresentation().setEnabledAndVisible(false);
    } else {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    try {
      BlazeImportJavaProjectWizard wizard = selectFileAndCreateWizard();
      if (wizard != null) {
        if (!wizard.runWizard()) {
          return;
        }
        //noinspection ConstantConditions
        NewProjectUtil.createFromWizard(wizard, null);
      }
    }
    catch (IOException | ConfigurationException exception) {
      handleImportException(e.getProject(), exception);
    }
  }

  @Nullable
  private BlazeImportJavaProjectWizard selectFileAndCreateWizard() throws IOException, ConfigurationException {
    VirtualFile fileToImport = BlazeImportFileChooser.getFileToImport();
    if (fileToImport == null) {
      return null;
    }
    return createImportWizard(fileToImport);
  }

  @Nullable
  protected BlazeImportJavaProjectWizard createImportWizard(VirtualFile file) throws IOException, ConfigurationException {
    ProjectImportProvider provider = createBlazeImportProvider();
    return new BlazeImportJavaProjectWizard(file, provider);
  }

  private static ProjectImportProvider createBlazeImportProvider() {
    BlazeNewJavaProjectImportBuilder builder = new BlazeNewJavaProjectImportBuilder();
    return new BlazeNewProjectImportProvider(builder);
  }

  private static void handleImportException(@Nullable Project project, Exception e) {
    String message = String.format("Project import failed: %s", e.getMessage());
    Messages.showErrorDialog(project, message, "Import Project");
    LOG.error(e);
  }
}
