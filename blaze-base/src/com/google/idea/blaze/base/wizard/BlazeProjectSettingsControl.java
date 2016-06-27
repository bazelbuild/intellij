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
package com.google.idea.blaze.base.wizard;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.ui.JPanelProvidingProject;
import com.google.idea.blaze.base.settings.ui.ProjectViewUi;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHelper;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The UI control to collect project settings when importing a Blaze project.
 */
public final class BlazeProjectSettingsControl {

  private static final FileChooserDescriptor STUDIO_PROJECT_FOLDER_DESCRIPTOR =
    new FileChooserDescriptor(false, true, false, false, false, false);
  private static final Logger LOG = Logger.getInstance(BlazeProjectSettingsControl.class);

  private WorkspaceRoot workspaceRoot;
  @Nullable private File sharedProjectViewFile;
  @Nullable private String vcsClientName;

  private TextFieldWithBrowseButton projectDataDirField;
  private JTextField projectNameField;
  private ProjectViewUi projectViewUi;

  public BlazeProjectSettingsControl(Disposable parentDisposable) {
    this.projectViewUi = new ProjectViewUi(parentDisposable);
  }

  public JPanel createComponent(File fileToImport) {
    JPanel component = new JPanelProvidingProject(ProjectViewUi.getProject(), new GridBagLayout());
    fillUi(component, 0);
    init(fileToImport);
    UiUtil.fillBottom(component);
    return component;
  }

  private void fillUi(@NotNull JPanel canvas, int indentLevel) {
    JLabel projectDataDirLabel = new JBLabel("Project data directory:");

    Dimension minSize = ProjectViewUi.getMinimumSize();
    // Add 120 pixels so we have room for our extra fields
    minSize.setSize(minSize.width, minSize.height + 120);
    canvas.setMinimumSize(minSize);
    canvas.setPreferredSize(minSize);

    projectDataDirField = new TextFieldWithBrowseButton();
    projectDataDirField
      .addBrowseFolderListener("", "Blaze Android Studio project data directory", null,
                               STUDIO_PROJECT_FOLDER_DESCRIPTOR,
                               TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT, false);
    final String dataDirToolTipText =
      "Directory in which to store the project's metadata. Choose a directory outside of"
      + " the Piper/CitC client directories or Git5 directory.";
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

  private void init(File fileToImport) {
    workspaceRoot = BuildSystemProvider.getWorkspaceRootProvider(BuildSystem.Blaze).findWorkspaceRoot(fileToImport);
    if (workspaceRoot == null) {
      throw new IllegalArgumentException("Invalid workspace root: " + fileToImport);
    }
    vcsClientName = BlazeVcsHelper.getClientName(workspaceRoot);

    File importDirectory = null;
    if (ProjectViewStorageManager.isProjectViewFile(fileToImport.getPath())) {
      importDirectory = fileToImport.getParentFile();
      sharedProjectViewFile = new File(fileToImport.getPath());
    }
    else if (ImportSource.isBuildFile(fileToImport)) {
      importDirectory = fileToImport.getParentFile();
      for (String extension : ProjectViewStorageManager.VALID_EXTENSIONS) {
        File defaultProjectViewFile = new File(fileToImport.getParentFile(), "." + extension);
        if (defaultProjectViewFile.exists()) {
          sharedProjectViewFile = defaultProjectViewFile;
          break;
        }
      }
    }

    String defaultProjectName = importDirectory != null
                                ? importDirectory.getName()
                                : workspaceRoot.directory().getParentFile().getName();
    projectNameField.setText(defaultProjectName);

    String defaultDataDir = getDefaultProjectDataDirectory(defaultProjectName, vcsClientName);
    projectDataDirField.setText(defaultDataDir);

    String projectViewText = "";
    if (sharedProjectViewFile != null) {
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
    }
    if (projectViewText.isEmpty() && importDirectory != null) {
      projectViewText = guessProjectViewFromLocation(workspaceRoot, importDirectory);
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


  @NotNull
  private static String getDefaultProjectDataDirectory(@NotNull String projectName, @Nullable String vcsClientName) {
    File defaultDataDirectory = new File(getDefaultProjectsDirectory());
    if (vcsClientName != null) {
      // Ensure that each client gets its own data directory.
      projectName = vcsClientName + "-" + projectName;
    }
    File desiredLocation = new File(defaultDataDirectory, projectName);
    return newUniquePath(desiredLocation);
  }

  @NotNull
  private static String getDefaultProjectsDirectory() {
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  @NotNull
  private static String guessProjectViewFromLocation(
    @NotNull WorkspaceRoot workspaceRoot,
    @NotNull File importDirectory) {

    WorkspacePath mainModuleGoogle3RelativePath = workspaceRoot.workspacePathFor(importDirectory);
    WorkspacePath testModuleGoogle3RelativePath = guessTestRelativePath(
      workspaceRoot,
      mainModuleGoogle3RelativePath);

    ListSection.Builder<DirectoryEntry> directorySectionBuilder = ListSection.builder(DirectorySection.KEY);
    directorySectionBuilder.add(DirectoryEntry.include(mainModuleGoogle3RelativePath));
    if (testModuleGoogle3RelativePath != null) {
      directorySectionBuilder.add(DirectoryEntry.include(testModuleGoogle3RelativePath));
    }

    ListSection.Builder<TargetExpression> targetSectionBuilder = ListSection.builder(TargetSection.KEY);
    targetSectionBuilder.add(TargetExpression.fromString("//" + mainModuleGoogle3RelativePath + "/...:all"));
    if (testModuleGoogle3RelativePath != null) {
      targetSectionBuilder.add(TargetExpression.fromString("//" + testModuleGoogle3RelativePath + "/...:all"));
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
    @NotNull WorkspaceRoot workspaceRoot,
    @NotNull WorkspacePath projectWorkspacePath) {
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

  @Nullable
  private BlazeValidationError validateProjectDataDirectory(@NotNull File projectDataDirPath) {
    if (workspaceRoot.isInWorkspace(projectDataDirPath)) {
      return new BlazeValidationError(
        "Project data directory should be placed outside of the client directory"
      );
    }
    return null;
  }

  @NotNull
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
    BlazeValidationError projectDataDirectoryValidation = validateProjectDataDirectory(projectDataDir);
    if (projectDataDirectoryValidation != null) {
      return BlazeValidationResult.failure(projectDataDirectoryValidation);
    }

    List<IssueOutput> issues = Lists.newArrayList();

    ProjectViewSet projectViewSet = projectViewUi.parseProjectView(issues);
    BlazeValidationError projectViewParseError = validationErrorFromIssueList(issues);
    if (projectViewParseError != null) {
      return BlazeValidationResult.failure(projectViewParseError);
    }

    return BlazeValidationResult.success();
  }

  public ImportResults getResults() {
    String projectName = projectNameField.getText().trim();
    String projectDataDirectory = projectDataDirField.getText().trim();

    // Create unique location hash
    final String locationHash = createLocationHash(projectName);

    // Only support blaze in the old import wizard. TODO: remove this wizard prior to public bazel release.
    BuildSystem fixedBuildSystem = BuildSystem.Blaze;

    File sharedProjectViewFile = this.sharedProjectViewFile;
    File localProjectViewFile = ProjectViewStorageManager.getLocalProjectViewFileName(fixedBuildSystem, new File(projectDataDirectory));

    BlazeImportSettings importSettings = new BlazeImportSettings(
      workspaceRoot.directory().getPath(),
      projectName,
      projectDataDirectory,
      locationHash,
      localProjectViewFile.getPath(),
      fixedBuildSystem
    );

    boolean useSharedProjectView = projectViewUi.getUseSharedProjectView();

    // If we're using a shared project view, synthesize a local one that imports the shared one
    final ProjectView projectView;
    if (useSharedProjectView && sharedProjectViewFile != null) {
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
      projectView = ProjectView.builder()
        .put(ScalarSection.builder(ImportSection.KEY)
          .set(workspaceRoot.workspacePathFor(sharedProjectViewFile)))
        .build();
    } else {
      ProjectViewSet parseResult = projectViewUi.parseProjectView(Lists.<IssueOutput>newArrayList());
      ProjectViewSet.ProjectViewFile projectViewFile = parseResult.getTopLevelProjectViewFile();
      assert projectViewFile != null;
      projectView = projectViewFile.projectView;
    }

    return new ImportResults(
      importSettings,
      projectView,
      calculateProjectName(projectName, vcsClientName),
      projectDataDirectory
    );
  }

  @NotNull
  private static String createLocationHash(@NotNull String projectName) {
    String uuid = UUID.randomUUID().toString();
    uuid = uuid.substring(0, Math.min(uuid.length(), 8));
    return projectName.replaceAll("[^a-zA-Z0-9]", "") + "-" + uuid;
  }

  private static String calculateProjectName(
    @NotNull String projectName,
    @Nullable String vcsClientName) {
    if (vcsClientName != null) {
      projectName = String.format("%s (%s)", projectName, vcsClientName);
    }
    return projectName;
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
}
