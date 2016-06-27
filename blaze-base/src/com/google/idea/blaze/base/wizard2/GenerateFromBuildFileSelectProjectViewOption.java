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

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.panels.HorizontalLayout;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;

class GenerateFromBuildFileSelectProjectViewOption implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH = "generate-from-build-file.last-workspace-path";
  private final BlazeNewProjectBuilder builder;
  private final BlazeWizardUserSettings userSettings;
  private final JTextField buildFilePathField;
  private final JPanel component;

  public GenerateFromBuildFileSelectProjectViewOption(
    BlazeNewProjectBuilder builder) {
    this.builder = builder;
    this.userSettings = builder.getUserSettings();

    String defaultWorkspacePath = userSettings.get(LAST_WORKSPACE_PATH, "");

    JPanel panel = new JPanel(new HorizontalLayout(10));
    JLabel pathLabel = new JLabel("BUILD file:");
    UiUtil.setPreferredWidth(pathLabel, HEADER_LABEL_WIDTH);
    panel.add(pathLabel);
    this.buildFilePathField = new JTextField();
    buildFilePathField.setText(defaultWorkspacePath);
    UiUtil.setPreferredWidth(buildFilePathField, MAX_INPUT_FIELD_WIDTH);
    panel.add(buildFilePathField);
    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());

    int buttonSize = buildFilePathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));
    panel.add(button);
    this.component = panel;
  }

  @Override
  public String getOptionName() {
    return "generate-from-build-file";
  }

  @Override
  public String getOptionText() {
    return "Generate from BUILD file";
  }

  @Override
  public JComponent getUiComponent() {
    return component;
  }

  @Override
  public BlazeValidationResult validate() {
    if (getBuildFilePath().isEmpty()) {
      return BlazeValidationResult.failure("BUILD file field cannot be empty.");
    }
    WorkspaceRoot workspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();
    File file = workspaceRoot.fileForPath(new WorkspacePath(getBuildFilePath()));
    if (!file.exists()) {
      return BlazeValidationResult.failure("BUILD file does not exist.");
    }

    return BlazeValidationResult.success();
  }

  @Nullable
  @Override
  public WorkspacePath getSharedProjectView() {
    return null;
  }

  @Nullable
  @Override
  public String getInitialProjectViewText() {
    WorkspaceRoot workspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();
    WorkspacePath workspacePath = new WorkspacePath(getBuildFilePath());
    return guessProjectViewFromLocation(workspaceRoot,
                                        workspaceRoot.workspacePathFor(workspaceRoot.fileForPath(workspacePath).getParentFile()));
  }

  @Override
  public void commit() {
    userSettings.put(LAST_WORKSPACE_PATH, getBuildFilePath());
  }

  private static String guessProjectViewFromLocation(WorkspaceRoot workspaceRoot, WorkspacePath workspacePath) {

    WorkspacePath mainModuleWorkspaceRelativePath = workspacePath;
    WorkspacePath testModuleWorkspaceRelativePath = guessTestRelativePath(
      workspaceRoot,
      mainModuleWorkspaceRelativePath);

    ListSection.Builder<DirectoryEntry> directorySectionBuilder = ListSection.builder(DirectorySection.KEY);
    directorySectionBuilder.add(DirectoryEntry.include(mainModuleWorkspaceRelativePath));
    if (testModuleWorkspaceRelativePath != null) {
      directorySectionBuilder.add(DirectoryEntry.include(testModuleWorkspaceRelativePath));
    }

    ListSection.Builder<TargetExpression> targetSectionBuilder = ListSection.builder(TargetSection.KEY);
    targetSectionBuilder.add(TargetExpression.allFromPackageRecursive(mainModuleWorkspaceRelativePath));
    if (testModuleWorkspaceRelativePath != null) {
      targetSectionBuilder.add(TargetExpression.allFromPackageRecursive(testModuleWorkspaceRelativePath));
    }

    return ProjectViewParser.projectViewToString(
      ProjectView.builder()
        .put(directorySectionBuilder)
        .put(targetSectionBuilder)
        .build()
    );
  }

  @Nullable
  private static WorkspacePath guessTestRelativePath(
    WorkspaceRoot workspaceRoot,
    WorkspacePath projectWorkspacePath) {
    String projectRelativePath = projectWorkspacePath.relativePath();
    String testBuildFileRelativePath = null;
    if (projectRelativePath.startsWith("java/")) {
      testBuildFileRelativePath = projectRelativePath.replaceFirst("java/", "javatests/");
    }
    else if (projectRelativePath.contains("/java/")) {
      testBuildFileRelativePath = projectRelativePath.replaceFirst("/java/", "/javatests/");
    }
    if (testBuildFileRelativePath != null) {
      File testBuildFile = workspaceRoot.fileForPath(new WorkspacePath(testBuildFileRelativePath));
      if (testBuildFile.exists()) {
        return new WorkspacePath(testBuildFileRelativePath);
      }
    }
    return null;
  }

  private String getBuildFilePath() {
    return buildFilePathField.getText().trim();
  }

  private void chooseWorkspacePath() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
      .withShowHiddenFiles(true) // Show root project view file
      .withHideIgnored(false)
      .withTitle("Select BUILD File")
      .withDescription("Select a BUILD file to synthesize a project view from.")
      .withFileFilter(virtualFile -> virtualFile.getName().equals("BUILD"));
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    WorkspaceRoot workspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();

    File startingLocation = workspaceRoot.directory();
    String buildFilePath = getBuildFilePath();
    if (!buildFilePath.isEmpty()) {
      File fileLocation = workspaceRoot.fileForPath(new WorkspacePath(buildFilePath));
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
    String newWorkspacePath = FileUtil.getRelativePath(workspaceRoot.directory(), new File(file.getPath()));
    buildFilePathField.setText(newWorkspacePath);
  }
}
