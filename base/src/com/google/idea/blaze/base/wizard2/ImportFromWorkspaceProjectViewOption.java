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
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithStoredHistory;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Imports a project view from source control */
public class ImportFromWorkspaceProjectViewOption implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH = "import-from-workspace.last-workspace-path";

  final BlazeNewProjectBuilder builder;
  final BlazeWizardUserSettings userSettings;
  final JComponent component;
  final TextFieldWithStoredHistory projectViewPathField;

  public ImportFromWorkspaceProjectViewOption(BlazeNewProjectBuilder builder) {
    this.builder = builder;
    this.userSettings = builder.getUserSettings();

    this.projectViewPathField = new TextFieldWithStoredHistory(LAST_WORKSPACE_PATH);
    projectViewPathField.setName("projectview-file-path-field");
    projectViewPathField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    projectViewPathField.setText(userSettings.get(LAST_WORKSPACE_PATH, ""));

    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());
    int buttonSize = projectViewPathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP, new JLabel("Project view:"), projectViewPathField, button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
  }

  @Override
  public String getOptionName() {
    return "import-from-workspace";
  }

  @Override
  public String getOptionText() {
    return "Import project view file";
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
    String error = WorkspacePath.validate(getProjectViewPath());
    if (error != null) {
      return BlazeValidationResult.failure(error);
    }
    WorkspacePathResolver workspacePathResolver =
        builder.getWorkspaceOption().getWorkspacePathResolver();
    File file = workspacePathResolver.resolveToFile(getSharedProjectView());
    if (!file.exists()) {
      return BlazeValidationResult.failure("Project view file does not exist.");
    }
    if (file.isDirectory()) {
      return BlazeValidationResult.failure("Specified path is a directory, not a file");
    }

    return BlazeValidationResult.success();
  }

  @Nullable
  @Override
  public WorkspacePath getSharedProjectView() {
    return new WorkspacePath(getProjectViewPath());
  }

  @Override
  public String getImportDirectory() {
    File projectViewFile = new File(getProjectViewPath());
    File projectViewDirectory = projectViewFile.getParentFile();
    if (projectViewDirectory == null) {
      return null;
    }
    return projectViewDirectory.getName();
  }

  @Override
  public void commit() {
    userSettings.put(LAST_WORKSPACE_PATH, getProjectViewPath());
    projectViewPathField.addCurrentTextToHistory();
  }

  private String getProjectViewPath() {
    return projectViewPathField.getText().trim();
  }

  private void chooseWorkspacePath() {
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(true, false, false, false, false, false)
            .withShowHiddenFiles(true) // Show root project view file
            .withHideIgnored(false)
            .withTitle("Select Project View File")
            .withDescription("Select a project view file to import.")
            .withFileFilter(
                virtualFile ->
                    ProjectViewStorageManager.isProjectViewFile(new File(virtualFile.getPath())));
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    WorkspacePathResolver workspacePathResolver =
        builder.getWorkspaceOption().getWorkspacePathResolver();
    File fileBrowserRoot = builder.getWorkspaceOption().getFileBrowserRoot();
    File startingLocation = fileBrowserRoot;
    String projectViewPath = getProjectViewPath();
    if (!projectViewPath.isEmpty()) {
      // If the user has typed part of the path then clicked the '...', try to start from the
      // partial state
      projectViewPath = StringUtil.trimEnd(projectViewPath, '/');
      if (WorkspacePath.isValid(projectViewPath)) {
        File fileLocation = workspacePathResolver.resolveToFile(new WorkspacePath(projectViewPath));
        if (fileLocation.exists()
            && FileUtil.isAncestor(
                getCanonicalPathSafe(fileBrowserRoot), getCanonicalPathSafe(fileLocation), true)) {
          startingLocation = fileLocation;
        }
      }
    }
    VirtualFile toSelect =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(startingLocation.getPath());
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];

    if (!FileUtil.isAncestor(
        getCanonicalPathSafe(fileBrowserRoot), getCanonicalPathSafe(file), true)) {
      Messages.showErrorDialog(
          String.format(
              "You must choose a project view file under %s. "
                  + "To use an external project view, please use the 'Copy external' option.",
              fileBrowserRoot.getPath()),
          "Cannot Use Project View File");
      return;
    }

    String newWorkspacePath =
        FileUtil.getRelativePath(
            getCanonicalPathSafe(fileBrowserRoot),
            getCanonicalPathSafe(new File(file.getPath())),
            File.separatorChar);
    projectViewPathField.setText(newWorkspacePath);
  }

  /**
   * Tries to resolve the canonical path of the file. If unsuccessful, falls back to returning the
   * raw path.
   */
  private static String getCanonicalPathSafe(File file) {
    try {
      return file.getCanonicalPath();
    } catch (IOException e) {
      return file.getPath();
    }
  }

  /**
   * Tries to resolve the canonical path of the file. If unsuccessful, falls back to returning the
   * raw path.
   */
  private static String getCanonicalPathSafe(VirtualFile file) {
    String canonicalPath = file.getCanonicalPath();
    return canonicalPath != null ? canonicalPath : file.getPath();
  }
}
