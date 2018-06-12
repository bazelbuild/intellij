/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.settings.ui;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSection.Builder;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.RelatedWorkspacePathFinder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.WorkspaceFileTextField;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.SwingHelper;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Adds a directory to the project then syncs. */
public final class AddDirectoryToProjectAction extends BlazeProjectAction {

  private static final String ADD_TARGETS_WARNING_TEXT =
      "This will add all blaze targets below this directory to your project. This could have a "
          + "large impact on your project build times if the directory contains a lot of code or "
          + "expensive genrule targets.";

  private static final String NO_TARGETS_WARNING_TEXT =
      "Adding a directory without adding targets means that references in the source files may not "
          + "resolve correctly.";

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    runAction(project, null);
  }

  /**
   * Kick off the 'add directory to project' action. If {@code fixedDirectory} is set, the user
   * won't be able to change which directory is being added.
   */
  public static void runAction(Project project, @Nullable File fixedDirectory) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return;
    }
    OpenBlazeWorkspaceFileActionDialog dialog =
        new OpenBlazeWorkspaceFileActionDialog(project, projectData.workspacePathResolver);
    if (fixedDirectory != null) {
      dialog.fileTextField.getField().setText(fixedDirectory.getAbsolutePath());
      dialog.fileTextField.getField().setEnabled(false);
    }
    dialog.show();
  }

  private static class OpenBlazeWorkspaceFileActionDialog extends DialogWrapper {

    static final int PATH_FIELD_WIDTH = 40;
    final Project project;
    final WorkspacePathResolver workspacePathResolver;
    final JPanel component;
    final FileTextField fileTextField;
    final JBCheckBox addTargetsCheckBox;

    OpenBlazeWorkspaceFileActionDialog(
        Project project, WorkspacePathResolver workspacePathResolver) {
      super(project, /* canBeParent */ false, IdeModalityType.PROJECT);
      this.project = project;
      this.workspacePathResolver = workspacePathResolver;

      FileChooserDescriptor descriptor =
          FileChooserDescriptorFactory.createSingleFolderDescriptor();
      fileTextField =
          WorkspaceFileTextField.create(
              workspacePathResolver, descriptor, PATH_FIELD_WIDTH, myDisposable);
      JBLabel directoryLabel =
          new JBLabel("Directory:", AllIcons.Modules.SourceFolder, SwingConstants.LEFT);
      JPanel directoryPanel =
          SwingHelper.newHorizontalPanel(
              Component.TOP_ALIGNMENT, directoryLabel, fileTextField.getField());
      addTargetsCheckBox = new JBCheckBox("Add build targets to the project", true);
      JBLabel warning =
          new JBLabel(
              "<html>" + ADD_TARGETS_WARNING_TEXT + "</html>",
              AllIcons.General.BalloonWarning,
              SwingConstants.LEFT);
      warning.setPreferredSize(new Dimension(800, 100));

      addTargetsCheckBox.addChangeListener(
          e -> {
            String warningText;
            if (addTargetsCheckBox.isSelected()) {
              warningText = ADD_TARGETS_WARNING_TEXT;
            } else {
              warningText = NO_TARGETS_WARNING_TEXT;
            }
            warning.setText("<html>" + warningText + "</html>");
          });

      component =
          SwingHelper.newLeftAlignedVerticalPanel(
              directoryPanel, addTargetsCheckBox, warning, Box.createVerticalGlue());

      setTitle("Add Directory to Project");

      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return component;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return fileTextField.getField().isEnabled() ? fileTextField.getField() : null;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      VirtualFile selectedFile = fileTextField.getSelectedFile();
      if (selectedFile == null || !selectedFile.exists()) {
        return new ValidationInfo("File does not exist", fileTextField.getField());
      } else if (!selectedFile.isDirectory()) {
        return new ValidationInfo("File is not a directory", fileTextField.getField());
      }

      WorkspacePath workspacePath =
          workspacePathResolver.getWorkspacePath(new File(selectedFile.getPath()));
      if (workspacePath == null) {
        return new ValidationInfo("File is not in workspace", fileTextField.getField());
      }

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      checkState(projectViewSet != null);

      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      checkState(importSettings != null);
      if (importSettings.getBuildSystem() == BuildSystem.Blaze && workspacePath.isWorkspaceRoot()) {
        return new ValidationInfo(
            String.format(
                "Cannot add the workspace root '%s' to the project.\n"
                    + "This will destroy performance.",
                selectedFile.getPath()));
      }

      ImportRoots importRoots =
          ImportRoots.builder(
                  WorkspaceRoot.fromImportSettings(importSettings), importSettings.getBuildSystem())
              .add(projectViewSet)
              .build();

      if (importRoots.containsWorkspacePath(workspacePath)) {
        return new ValidationInfo("This directory is already included in your project");
      }

      return null;
    }

    @Override
    protected void doOKAction() {
      VirtualFile selectedFile = fileTextField.getSelectedFile();
      checkState(selectedFile != null);
      WorkspacePath workspacePath =
          workspacePathResolver.getWorkspacePath(new File(selectedFile.getPath()));
      checkState(workspacePath != null);

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      checkState(projectViewSet != null);

      Set<DirectoryEntry> existingDirectories =
          ImmutableSet.copyOf(projectViewSet.listItems(DirectorySection.KEY));
      Set<TargetExpression> existingTargets =
          ImmutableSet.copyOf(projectViewSet.listItems(TargetSection.KEY));

      Set<WorkspacePath> pathsToAdd = new LinkedHashSet<>();
      pathsToAdd.add(workspacePath);
      pathsToAdd.addAll(
          RelatedWorkspacePathFinder.getInstance()
              .findRelatedWorkspaceDirectories(workspacePathResolver, workspacePath));

      Set<DirectoryEntry> newDirectories =
          pathsToAdd
              .stream()
              .map(DirectoryEntry::include)
              .filter(entry -> !existingDirectories.contains(entry))
              .collect(toCollection(LinkedHashSet::new));

      Set<TargetExpression> newTargets =
          pathsToAdd
              .stream()
              .map(TargetExpression::allFromPackageRecursive)
              .filter(entry -> !existingTargets.contains(entry))
              .collect(toCollection(LinkedHashSet::new));

      boolean addTargets = addTargetsCheckBox.isSelected();

      ProjectViewEdit edit =
          ProjectViewEdit.editLocalProjectView(
              project,
              builder -> {
                ListSection<DirectoryEntry> directories = builder.getLast(DirectorySection.KEY);
                Builder<DirectoryEntry> directoriesUpdater =
                    ListSection.update(DirectorySection.KEY, directories);
                newDirectories.forEach(directoriesUpdater::add);
                builder.replace(directories, directoriesUpdater);

                if (addTargets) {
                  ListSection<TargetExpression> targets = builder.getLast(TargetSection.KEY);
                  Builder<TargetExpression> targetsUpdater =
                      ListSection.update(TargetSection.KEY, targets);
                  newTargets.forEach(targetsUpdater::add);
                  builder.replace(targets, targetsUpdater);
                }

                return true;
              });

      if (edit == null) {
        Messages.showErrorDialog(
            "Could not modify project view. Check for errors in your project view and try again",
            "Error");
        return;
      }

      edit.apply();

      if (addTargets) {
        BlazeSyncManager.getInstance(project).partialSync(newTargets);
      } else {
        BlazeSyncManager.getInstance(project).directoryUpdate(/* inBackground */ true);
      }
      super.doOKAction();
    }
  }
}
