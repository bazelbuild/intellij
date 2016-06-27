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

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.panels.HorizontalLayout;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;

class ImportFromWorkspaceProjectViewOption implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH = "import-from-workspace.last-workspace-path";

  final BlazeNewProjectBuilder builder;
  final BlazeWizardUserSettings userSettings;
  final JComponent component;
  final JTextField projectViewPathField;

  ImportFromWorkspaceProjectViewOption(BlazeNewProjectBuilder builder) {
    this.builder = builder;
    this.userSettings = builder.getUserSettings();

    String defaultWorkspacePath = userSettings.get(LAST_WORKSPACE_PATH, "");

    JPanel panel = new JPanel(new HorizontalLayout(10));
    JLabel projectViewLabel = new JLabel("Project view:");
    UiUtil.setPreferredWidth(projectViewLabel, HEADER_LABEL_WIDTH);
    panel.add(projectViewLabel);
    this.projectViewPathField = new JTextField();
    projectViewPathField.setText(defaultWorkspacePath);
    UiUtil.setPreferredWidth(projectViewPathField, MAX_INPUT_FIELD_WIDTH);
    panel.add(projectViewPathField);
    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());
    int buttonSize = projectViewPathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));
    panel.add(button);
    this.component = panel;
  }

  @Override
  public String getOptionName() {
    return "import-from-workspace";
  }

  @Override
  public String getOptionText() {
    return "Import from workspace";
  }

  @Override
  public JComponent getUiComponent() {
    return component;
  }

  @Override
  public BlazeValidationResult validate() {
    if (getProjectViewPath().isEmpty()) {
      return BlazeValidationResult.failure("Workspace path to project view file cannot be empty.");
    }
    WorkspaceRoot workspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();
    File file = workspaceRoot.fileForPath(getSharedProjectView());
    if (!file.exists()) {
      return BlazeValidationResult.failure("Project view file does not exist.");
    }

    return BlazeValidationResult.success();
  }

  @Nullable
  @Override
  public WorkspacePath getSharedProjectView() {
    return new WorkspacePath(getProjectViewPath());
  }

  @Nullable
  @Override
  public String getInitialProjectViewText() {
    return null;
  }

  @Override
  public void commit() {
    userSettings.put(LAST_WORKSPACE_PATH, getProjectViewPath());
  }

  private String getProjectViewPath() {
    return projectViewPathField.getText().trim();
  }

  private void chooseWorkspacePath() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
      .withShowHiddenFiles(true) // Show root project view file
      .withHideIgnored(false)
      .withTitle("Select Project View File")
      .withDescription("Select a project view file to import.")
      .withFileFilter(virtualFile -> ProjectViewStorageManager.isProjectViewFile(new File(virtualFile.getPath())));
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    WorkspaceRoot workspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();

    File startingLocation = workspaceRoot.directory();
    String projectViewPath = getProjectViewPath();
    if (!projectViewPath.isEmpty()) {
      File fileLocation = workspaceRoot.fileForPath(new WorkspacePath(projectViewPath));
      if (fileLocation.exists()) {
        startingLocation = fileLocation;
      }
    }
    VirtualFile toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(startingLocation.getPath());
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];

    if (!FileUtil.isAncestor(workspaceRoot.directory().getPath(), file.getPath(), true)) {
      Messages.showErrorDialog(
        String.format(
          "You must choose a project view file under %s. To use an external project view, please use the 'Copy external' option.",
          workspaceRoot.directory().getPath()
        ),
        "Cannot Use Project View File"
      );
      return;
    }

    String newWorkspacePath = FileUtil.getRelativePath(workspaceRoot.directory(), new File(file.getPath()));
    projectViewPathField.setText(newWorkspacePath);
  }
}
