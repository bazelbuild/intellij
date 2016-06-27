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

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.panels.HorizontalLayout;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;

public abstract class UseExistingWorkspaceOption implements BlazeSelectWorkspaceOption {

  private final BlazeWizardUserSettings userSettings;
  private final JComponent component;
  private final JTextField directoryField;
  private final BuildSystem buildSystem;

  protected UseExistingWorkspaceOption(BlazeNewProjectBuilder builder, BuildSystem buildSystem) {
    this.userSettings = builder.getUserSettings();
    this.buildSystem = buildSystem;

    String defaultDirectory = userSettings.get(BlazeNewProjectBuilder.lastImportedWorkspaceKey(buildSystem), "");

    JPanel panel = new JPanel(new HorizontalLayout(10));
    panel.add(getIconComponent());
    JLabel workspaceRootLabel = new JLabel("Workspace:");
    UiUtil.setPreferredWidth(workspaceRootLabel, HEADER_LABEL_WIDTH);
    panel.add(workspaceRootLabel);
    this.directoryField = new JTextField();
    directoryField.setText(defaultDirectory);
    UiUtil.setPreferredWidth(directoryField, MAX_INPUT_FIELD_WIDTH);
    panel.add(directoryField);
    JButton button = new JButton("...");
    button.addActionListener(action -> chooseDirectory());
    int buttonSize = directoryField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));
    panel.add(button);
    this.component = panel;
  }

  protected abstract boolean isWorkspaceRoot(VirtualFile file);

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
  public WorkspaceRoot commit() throws BlazeProjectCommitException {
    return new WorkspaceRoot(new File(getDirectory()));
  }

  @Nullable
  @Override
  public WorkspaceRoot getTemporaryWorkspaceRoot() {
    return new WorkspaceRoot(new File(getDirectory()));
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

    File workspaceRoot = new File(getDirectory());
    if (!workspaceRoot.exists()) {
      return BlazeValidationResult.failure("Workspace does not exist");
    }
    return BlazeValidationResult.success();
  }

  private String getDirectory() {
    return directoryField.getText().trim();
  }

  private void chooseDirectory() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false)
    {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        // Default implementation doesn't filter directories, we want to make sure only workspace roots are selectable
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
    }
      .withHideIgnored(false)
      .withTitle("Select Workspace Root")
      .withDescription(fileChooserDescription())
      .withFileFilter(this::isWorkspaceRoot);
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    final VirtualFile[] files;
    File existingLocation = new File(getDirectory());
    if (existingLocation.exists()) {
      VirtualFile toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(existingLocation.getPath());
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

  private Component getIconComponent() {
    JLabel iconPanel = new JLabel(IconLoader.getIconSnapshot(getBuildSystemIcon())) {
      @Override
      public boolean isEnabled() {
        return true;
      }
    };
    UiUtil.setPreferredWidth(iconPanel, 16);
    return iconPanel;
  }
}
