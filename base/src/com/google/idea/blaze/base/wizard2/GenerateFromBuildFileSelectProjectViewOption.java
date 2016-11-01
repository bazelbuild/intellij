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

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithStoredHistory;
import java.awt.Dimension;
import java.io.File;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

class GenerateFromBuildFileSelectProjectViewOption implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH = "generate-from-build-file.last-workspace-path";
  private final BlazeNewProjectBuilder builder;
  private final BlazeWizardUserSettings userSettings;
  private final TextFieldWithStoredHistory buildFilePathField;
  private final JComponent component;

  public GenerateFromBuildFileSelectProjectViewOption(BlazeNewProjectBuilder builder) {
    this.builder = builder;
    this.userSettings = builder.getUserSettings();

    this.buildFilePathField = new TextFieldWithStoredHistory(LAST_WORKSPACE_PATH);
    buildFilePathField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    buildFilePathField.setText(userSettings.get(LAST_WORKSPACE_PATH, ""));

    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());
    int buttonSize = buildFilePathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP, new JLabel("BUILD file:"), buildFilePathField, button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
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
    WorkspaceRoot temporaryWorkspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();
    File file = temporaryWorkspaceRoot.fileForPath(new WorkspacePath(getBuildFilePath()));
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
    WorkspaceRoot temporaryWorkspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();
    WorkspacePath workspacePath = new WorkspacePath(getBuildFilePath());
    return guessProjectViewFromLocation(
        temporaryWorkspaceRoot,
        temporaryWorkspaceRoot.workspacePathFor(
            temporaryWorkspaceRoot.fileForPath(workspacePath).getParentFile()));
  }

  @Override
  public void commit() {
    userSettings.put(LAST_WORKSPACE_PATH, getBuildFilePath());
    buildFilePathField.addCurrentTextToHistory();
  }

  private static String guessProjectViewFromLocation(
      WorkspaceRoot workspaceRoot, WorkspacePath workspacePath) {

    WorkspacePath mainModuleWorkspaceRelativePath = workspacePath;
    WorkspacePath testModuleWorkspaceRelativePath =
        guessTestRelativePath(workspaceRoot, mainModuleWorkspaceRelativePath);

    ListSection.Builder<DirectoryEntry> directorySectionBuilder =
        ListSection.builder(DirectorySection.KEY);
    directorySectionBuilder.add(DirectoryEntry.include(mainModuleWorkspaceRelativePath));
    if (testModuleWorkspaceRelativePath != null) {
      directorySectionBuilder.add(DirectoryEntry.include(testModuleWorkspaceRelativePath));
    }

    ListSection.Builder<TargetExpression> targetSectionBuilder =
        ListSection.builder(TargetSection.KEY);
    targetSectionBuilder.add(
        TargetExpression.allFromPackageRecursive(mainModuleWorkspaceRelativePath));
    if (testModuleWorkspaceRelativePath != null) {
      targetSectionBuilder.add(
          TargetExpression.allFromPackageRecursive(testModuleWorkspaceRelativePath));
    }

    return ProjectViewParser.projectViewToString(
        ProjectView.builder()
            .add(directorySectionBuilder)
            .add(TextBlockSection.of(TextBlock.newLine()))
            .add(targetSectionBuilder)
            .build());
  }

  @Nullable
  private static WorkspacePath guessTestRelativePath(
      WorkspaceRoot workspaceRoot, WorkspacePath projectWorkspacePath) {
    String projectRelativePath = projectWorkspacePath.relativePath();
    String testBuildFileRelativePath = null;
    if (projectRelativePath.startsWith("java/")) {
      testBuildFileRelativePath = projectRelativePath.replaceFirst("java/", "javatests/");
    } else if (projectRelativePath.contains("/java/")) {
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
    BuildSystemProvider buildSystem =
        BuildSystemProvider.getBuildSystemProvider(builder.getBuildSystem());
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(true, false, false, false, false, false)
            .withShowHiddenFiles(true) // Show root project view file
            .withHideIgnored(false)
            .withTitle("Select BUILD File")
            .withDescription("Select a BUILD file to synthesize a project view from.")
            .withFileFilter(virtualFile -> buildSystem.isBuildFile(virtualFile.getName()));
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    WorkspaceRoot temporaryWorkspaceRoot = builder.getWorkspaceOption().getTemporaryWorkspaceRoot();

    File startingLocation = temporaryWorkspaceRoot.directory();
    String buildFilePath = getBuildFilePath();
    if (!buildFilePath.isEmpty() && WorkspacePath.validate(buildFilePath)) {
      // If the user has typed part of the path then clicked the '...', try to start from the
      // partial state
      buildFilePath = StringUtil.trimEnd(buildFilePath, '/');
      if (WorkspacePath.validate(buildFilePath)) {
        File fileLocation = temporaryWorkspaceRoot.fileForPath(new WorkspacePath(buildFilePath));
        if (fileLocation.exists()) {
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
    String newWorkspacePath =
        FileUtil.getRelativePath(temporaryWorkspaceRoot.directory(), new File(file.getPath()));
    buildFilePathField.setText(newWorkspacePath);
  }
}
