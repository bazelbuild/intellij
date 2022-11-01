/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.SystemProperties;
import java.awt.Dimension;
import java.io.File;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Option to generate a project from a project proto.
 *
 * <p>This is prototype functionality only.
 */
public class GenerateFromProjectProto implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH =
      "generate-from-project-proto.last-workspace-path";

  private final BlazeNewProjectBuilder builder;
  private final BlazeWizardUserSettings userSettings;
  private final TextFieldWithStoredHistory projectProtoPathField;
  private final JComponent component;

  public GenerateFromProjectProto(BlazeNewProjectBuilder builder) {
    this.builder = builder;
    this.userSettings = builder.getUserSettings();

    this.projectProtoPathField = new TextFieldWithStoredHistory(LAST_WORKSPACE_PATH);
    projectProtoPathField.setName("project-proto-path-field");
    projectProtoPathField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    projectProtoPathField.setText(userSettings.get(LAST_WORKSPACE_PATH, ""));
    projectProtoPathField.setMinimumAndPreferredWidth(MINIMUM_FIELD_WIDTH);

    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());
    int buttonSize = projectProtoPathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP, new JLabel("Proto file:"), projectProtoPathField, button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
  }

  @Override
  public String getOptionName() {
    return "generate-from-project-proto";
  }

  @Override
  public String getDescription() {
    return "Generate from project proto (experimental)";
  }

  @Override
  public void optionDeselected() {
    BlazeSelectProjectViewOption.super.optionDeselected();
  }

  @Override
  public void validateAndUpdateBuilder(BlazeNewProjectBuilder builder)
      throws ConfigurationException {
    String projectProtoFile = getProjectProtoPath();
    String baseName = Iterables.getFirst(Splitter.on('.').split(projectProtoFile), "");
    String projectName = new File(baseName).getName();
    builder.setProjectName(projectName);
    builder.setProjectProtoFile(projectProtoFile);
    builder.setProjectDataDirectory(getProjectDataDirectory(projectName));
  }

  @Nullable
  @Override
  public String getInitialProjectViewText() {
    return String.format(Locale.US, "# Imported from project proto %s", getProjectProtoPath());
  }

  @Override
  public void commit() throws BlazeProjectCommitException {
    userSettings.put(LAST_WORKSPACE_PATH, getProjectProtoPath());
    projectProtoPathField.addCurrentTextToHistory();
  }

  @Nullable
  @Override
  public JComponent getUiComponent() {
    return component;
  }

  private String getProjectProtoPath() {
    return projectProtoPathField.getText().trim();
  }

  private void chooseWorkspacePath() {
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(true, false, false, false, false, false)
            .withShowHiddenFiles(true)
            .withHideIgnored(false)
            .withTitle("Select project proto file")
            .withDescription("Select project proto to load project from.");

    descriptor.setForcedToUseIdeaFileChooser(true);
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    WorkspacePathResolver workspacePathResolver =
        builder.getWorkspaceData().workspacePathResolver();

    File fileBrowserRoot = new File("/");
    File startingLocation = fileBrowserRoot;
    String protoFilePath = getProjectProtoPath();
    if (!protoFilePath.isEmpty() && WorkspacePath.isValid(protoFilePath)) {
      // If the user has typed part of the path then clicked the '...', try to start from the
      // partial state
      protoFilePath = StringUtil.trimEnd(protoFilePath, '/');
      if (WorkspacePath.isValid(protoFilePath)) {
        File fileLocation = workspacePathResolver.resolveToFile(new WorkspacePath(protoFilePath));
        if (fileLocation.exists() && FileUtil.isAncestor(fileBrowserRoot, fileLocation, true)) {
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

    String newWorkspacePath = new File(file.getPath()).getAbsolutePath();
    projectProtoPathField.setText(newWorkspacePath);
  }

  private String getProjectDataDirectory(String projectName) {
    File canonicalProjectDataLocation = builder.getWorkspaceData().canonicalProjectDataLocation();
    if (canonicalProjectDataLocation != null) {
      return canonicalProjectDataLocation.getPath();
    }
    return newUniquePath(new File(getDefaultProjectsDirectory(), projectName));
  }

  // TODO(mathewi) this is lifted from BlazeEditProjectViewControl. Share it instead.
  /** Returns a unique file path by appending numbers until a non-collision is found. */
  private static String newUniquePath(File location) {
    if (!location.exists()) {
      return location.getAbsolutePath();
    }

    String name = location.getName();
    File directory = location.getParentFile();
    int tries = 0;
    while (true) {
      String candidateName = String.format("%s-%02d", name, tries);
      File candidateFile = new File(directory, candidateName);
      if (!candidateFile.exists()) {
        return candidateFile.getAbsolutePath();
      }
      tries++;
    }
  }

  // TODO(mathewi) this is lifted from BlazeEditProjectViewControl.
  private static File getDefaultProjectsDirectory() {
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return new File(userHome, productName.replace(" ", "") + "Projects");
  }
}
