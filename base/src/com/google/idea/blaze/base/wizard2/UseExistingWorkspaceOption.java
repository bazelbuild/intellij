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

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithHistory;
import java.awt.Dimension;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Option to use an existing workspace */
public abstract class UseExistingWorkspaceOption implements BlazeSelectWorkspaceOption {

  private final JComponent component;
  private final TextFieldWithHistory directoryField;
  private final BuildSystem buildSystem;

  protected UseExistingWorkspaceOption(BlazeNewProjectBuilder builder, BuildSystem buildSystem) {
    this.buildSystem = buildSystem;

    this.directoryField = new TextFieldWithHistory();
    directoryField.setHistory(builder.getWorkspaceHistory(buildSystem));
    directoryField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    directoryField.setText(builder.getLastImportedWorkspace(buildSystem));

    JButton button = new JButton("...");
    button.addActionListener(action -> chooseDirectory());
    int buttonSize = directoryField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP,
            getIconComponent(),
            new JLabel("Workspace:"),
            directoryField,
            button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
  }

  protected abstract boolean isWorkspaceRoot(File file);

  protected abstract BlazeValidationResult validateWorkspaceRoot(File workspaceRoot);

  private boolean isWorkspaceRoot(VirtualFile file) {
    return isWorkspaceRoot(new File(file.getPath()));
  }

  protected abstract String fileChooserDescription();

  protected abstract Icon getBuildSystemIcon();

  protected abstract String getWorkspaceName(File workspaceRoot);

  @Override
  public BuildSystem getBuildSystemForWorkspace() {
    return buildSystem;
  }

  @Override
  public JComponent getUiComponent() {
    return component;
  }

  @Override
  public void commit() throws BlazeProjectCommitException {}

  @Override
  public WorkspaceRoot getWorkspaceRoot() {
    return new WorkspaceRoot(new File(getDirectory()));
  }

  @Nullable
  @Override
  public WorkspaceRoot getTemporaryWorkspaceRoot() {
    return getWorkspaceRoot();
  }

  @Override
  public String getWorkspaceName() {
    File workspaceRoot = new File(getDirectory());
    return getWorkspaceName(workspaceRoot);
  }

  @Override
  public BlazeValidationResult validate() {
    if (getDirectory().isEmpty()) {
      return BlazeValidationResult.failure("Please select a workspace");
    }

    File workspaceRootFile = new File(getDirectory());
    if (!workspaceRootFile.exists()) {
      return BlazeValidationResult.failure("Workspace does not exist");
    }

    WorkspaceRoot workspaceRoot = new WorkspaceRoot(workspaceRootFile);
    boolean hasVcsHandler =
        Arrays.stream(BlazeVcsHandler.EP_NAME.getExtensions())
            .anyMatch(vcsHandler -> vcsHandler.handlesProject(buildSystem, workspaceRoot));
    if (!hasVcsHandler) {
      StringBuilder sb = new StringBuilder();
      sb.append("Workspace is not of a supported VCS type. ");
      sb.append("VCS types considered were: ");
      Joiner.on(", ")
          .appendTo(
              sb,
              Arrays.stream(BlazeVcsHandler.EP_NAME.getExtensions())
                  .map(BlazeVcsHandler::getVcsName)
                  .collect(Collectors.toList()));
      return BlazeValidationResult.failure(sb.toString());
    }

    return validateWorkspaceRoot(workspaceRootFile);
  }

  private String getDirectory() {
    return directoryField.getText().trim();
  }

  private void chooseDirectory() {
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(false, true, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            // Default implementation doesn't filter directories,
            // we want to make sure only workspace roots are selectable
            return super.isFileSelectable(file) && isWorkspaceRoot(file);
          }

          @Override
          public Icon getIcon(VirtualFile file) {
            if (buildSystem == BuildSystem.Bazel) {
              // isWorkspaceRoot requires file system calls -- it's too expensive
              return super.getIcon(file);
            }
            if (isWorkspaceRoot(file)) {
              return AllIcons.Nodes.SourceFolder;
            }
            return super.getIcon(file);
          }
        }.withHideIgnored(false)
            .withTitle("Select Workspace Root")
            .withDescription(fileChooserDescription())
            .withFileFilter(this::isWorkspaceRoot);
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    final VirtualFile[] files;
    File existingLocation = new File(getDirectory());
    if (existingLocation.exists()) {
      VirtualFile toSelect =
          LocalFileSystem.getInstance().refreshAndFindFileByPath(existingLocation.getPath());
      files = chooser.choose(null, toSelect);
    } else {
      files = chooser.choose(null);
    }
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];
    directoryField.setText(file.getPath());
  }

  private JComponent getIconComponent() {
    JLabel iconPanel =
        new JLabel(IconLoader.getIconSnapshot(getBuildSystemIcon())) {
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
    UiUtil.setPreferredWidth(iconPanel, 16);
    return iconPanel;
  }
}
