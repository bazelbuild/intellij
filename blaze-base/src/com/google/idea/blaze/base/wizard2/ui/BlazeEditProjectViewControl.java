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
package com.google.idea.blaze.base.wizard2.ui;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.ui.JPanelProvidingProject;
import com.google.idea.blaze.base.settings.ui.ProjectViewUi;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeSelectProjectViewOption;
import com.google.idea.blaze.base.wizard2.BlazeSelectWorkspaceOption;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The UI control to collect project settings when importing a Blaze project.
 */
public final class BlazeEditProjectViewControl {

  private static final FileChooserDescriptor PROJECT_FOLDER_DESCRIPTOR =
    new FileChooserDescriptor(false, true, false, false, false, false);
  private static final Logger LOG = Logger.getInstance(BlazeEditProjectViewControl.class);

  private final JPanel component;
  private final String buildSystemName;
  private final ProjectViewUi projectViewUi;

  private TextFieldWithBrowseButton projectDataDirField;
  private JTextField projectNameField;
  private HashCode paramsHash;

  public BlazeEditProjectViewControl(BlazeNewProjectBuilder builder, Disposable parentDisposable) {
    this.projectViewUi = new ProjectViewUi(parentDisposable);
    JPanel component = new JPanelProvidingProject(ProjectViewUi.getProject(), new GridBagLayout());
    fillUi(component, 0, parentDisposable);
    update(builder);
    UiUtil.fillBottom(component);
    this.component = component;
    this.buildSystemName = builder.getBuildSystemName();
  }

  public Component getUiComponent() {
    return component;
  }

  private void fillUi(JPanel canvas, int indentLevel, Disposable parentDisposable) {
    JLabel projectDataDirLabel = new JBLabel("Project data directory:");

    Dimension minSize = ProjectViewUi.getMinimumSize();
    // Add 120 pixels so we have room for our extra fields
    minSize.setSize(minSize.width, minSize.height + 120);
    canvas.setMinimumSize(minSize);
    canvas.setPreferredSize(minSize);

    projectDataDirField = new TextFieldWithBrowseButton();
    projectDataDirField.addBrowseFolderListener("", buildSystemName + " project data directory", null,
                                                PROJECT_FOLDER_DESCRIPTOR,
                                                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT, false);
    final String dataDirToolTipText =
      "Directory in which to store the project's metadata. Choose a directory outside of your workspace.";
    projectDataDirField.setToolTipText(dataDirToolTipText);
    projectDataDirLabel.setToolTipText(dataDirToolTipText);

    canvas.add(projectDataDirLabel, UiUtil.getLabelConstraints(indentLevel));
    canvas.add(projectDataDirField, UiUtil.getFillLineConstraints(0));

    JLabel projectNameLabel = new JLabel("Project name:");
    projectNameField = new JTextField();
    final String projectNameToolTipText =
      "Project display name.";
    projectNameField.setToolTipText(projectNameToolTipText);
    projectNameLabel.setToolTipText(projectNameToolTipText);
    canvas.add(projectNameLabel, UiUtil.getLabelConstraints(indentLevel));
    canvas.add(projectNameField, UiUtil.getFillLineConstraints(0));

    projectViewUi.fillUi(canvas, indentLevel);
  }

  public void update(BlazeNewProjectBuilder builder) {
    BlazeSelectWorkspaceOption workspaceOption = builder.getWorkspaceOption();
    BlazeSelectProjectViewOption projectViewOption = builder.getProjectViewOption();
    String workspaceName = workspaceOption.getWorkspaceName();
    WorkspaceRoot workspaceRoot = workspaceOption.getTemporaryWorkspaceRoot();
    WorkspacePath workspacePath = projectViewOption.getSharedProjectView();
    String initialProjectViewText = projectViewOption.getInitialProjectViewText();

    HashCode hashCode = Hashing.md5().newHasher()
      .putUnencodedChars(workspaceName)
      .putUnencodedChars(workspaceRoot.toString())
      .putUnencodedChars(workspacePath != null ? workspacePath.toString() : "")
      .putUnencodedChars(initialProjectViewText != null ? initialProjectViewText : "")
      .hash();

    // If any params have changed, reinit the control
    if (!hashCode.equals(paramsHash)) {
      this.paramsHash = hashCode;
      init(workspaceName, workspaceRoot, workspacePath, initialProjectViewText);
    }
  }

  private void init(String workspaceName,
                    WorkspaceRoot workspaceRoot,
                    @Nullable WorkspacePath sharedProjectView,
                    @Nullable String initialProjectViewText) {

    projectNameField.setText(workspaceName);
    String defaultDataDir = getDefaultProjectDataDirectory(workspaceName);
    projectDataDirField.setText(defaultDataDir);

    String projectViewText = "";
    File sharedProjectViewFile = null;

    if (sharedProjectView != null) {
      sharedProjectViewFile = workspaceRoot.fileForPath(sharedProjectView);

      try {
        projectViewText = ProjectViewStorageManager.getInstance().loadProjectView(sharedProjectViewFile);
        if (projectViewText == null) {
          LOG.error("Could not load project view: " + sharedProjectViewFile);
          projectViewText = "";
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    } else {
      projectViewText = initialProjectViewText;
      LOG.assertTrue(projectViewText != null);
    }

    projectViewUi.init(
      workspaceRoot,
      projectViewText,
      sharedProjectViewFile != null ? projectViewText : null,
      sharedProjectViewFile,
      sharedProjectViewFile != null,
      false /* allowEditShared - not allowed during import */
    );
  }

  private static String getDefaultProjectDataDirectory(String projectName) {
    File defaultDataDirectory = new File(getDefaultProjectsDirectory());
    File desiredLocation = new File(defaultDataDirectory, projectName);
    return newUniquePath(desiredLocation);
  }

  private static String getDefaultProjectsDirectory() {
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  /**
   * Returns a unique file path by appending numbers until a non-collision is found.
   */
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

  public BlazeValidationResult validate() {
    // Validate project settings fields
    String projectName = projectNameField.getText().trim();
    if (StringUtil.isEmpty(projectName)) {
      return BlazeValidationResult.failure(new BlazeValidationError("Project name is not specified"));
    }
    String projectDataDirPath = projectDataDirField.getText().trim();
    if (StringUtil.isEmpty(projectDataDirPath)) {
      return BlazeValidationResult.failure(new BlazeValidationError("Project data directory is not specified"));
    }
    File projectDataDir = new File(projectDataDirPath);
    if (!projectDataDir.isAbsolute()) {
      return BlazeValidationResult.failure(new BlazeValidationError("Project data directory is not valid"));
    }

    List<IssueOutput> issues = Lists.newArrayList();

    projectViewUi.parseProjectView(issues);
    BlazeValidationError projectViewParseError = validationErrorFromIssueList(issues);
    if (projectViewParseError != null) {
      return BlazeValidationResult.failure(projectViewParseError);
    }

    return BlazeValidationResult.success();
  }

  @Nullable
  private static BlazeValidationError validationErrorFromIssueList(List<IssueOutput> issues) {
    List<IssueOutput> errors = issues
      .stream()
      .filter(issue -> issue.getCategory() == IssueOutput.Category.ERROR)
      .collect(Collectors.toList());

    if (!errors.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder();
      errorMessage.append("The following issues were found:\n\n");
      for (IssueOutput issue : errors) {
        errorMessage.append(issue.getMessage());
        errorMessage.append('\n');
      }
      return new BlazeValidationError(errorMessage.toString());
    }
    return null;
  }

  public void updateBuilder(BlazeNewProjectBuilder builder) {
    String projectName = projectNameField.getText().trim();
    String projectDataDirectory = projectDataDirField.getText().trim();
    File localProjectViewFile = ProjectViewStorageManager.getLocalProjectViewFileName(
      builder.getBuildSystem(), new File(projectDataDirectory)
    );

    BlazeSelectProjectViewOption selectProjectViewOption = builder.getProjectViewOption();
    boolean useSharedProjectView = projectViewUi.getUseSharedProjectView();


    // If we're using a shared project view, synthesize a local one that imports the shared one
    ProjectViewSet parseResult = projectViewUi.parseProjectView(Lists.newArrayList());

    final ProjectView projectView;
    final ProjectViewSet projectViewSet;
    if (useSharedProjectView && selectProjectViewOption.getSharedProjectView() != null) {
      projectView = ProjectView.builder()
        .put(ScalarSection.builder(ImportSection.KEY)
               .set(selectProjectViewOption.getSharedProjectView()))
        .build();
      projectViewSet = ProjectViewSet.builder()
        .addAll(parseResult.getProjectViewFiles())
        .add(localProjectViewFile, projectView)
        .build();
    } else {
      ProjectViewSet.ProjectViewFile projectViewFile = parseResult.getTopLevelProjectViewFile();
      assert projectViewFile != null;
      projectView = projectViewFile.projectView;
      projectViewSet = parseResult;
    }

    builder
      .setProjectView(projectView)
      .setProjectViewFile(localProjectViewFile)
      .setProjectViewSet(projectViewSet)
      .setProjectName(projectName)
      .setProjectDataDirectory(projectDataDirectory);
  }
}
