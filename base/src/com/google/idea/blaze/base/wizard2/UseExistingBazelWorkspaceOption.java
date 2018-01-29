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

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithHistory;
import icons.BlazeIcons;
import java.awt.Dimension;
import java.io.File;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Allows importing an existing bazel workspace */
public class UseExistingBazelWorkspaceOption implements BlazeSelectWorkspaceOption {

  private final JComponent component;
  private final TextFieldWithHistory directoryField;
  private final Map<String, File> vcsRootCache = Maps.newHashMap();

  public UseExistingBazelWorkspaceOption(BlazeNewProjectBuilder builder) {
    this.directoryField = new TextFieldWithHistory();
    this.directoryField.setName("workspace-directory-field");
    this.directoryField.setHistory(builder.getWorkspaceHistory(BuildSystem.Bazel));
    this.directoryField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    this.directoryField.setText(builder.getLastImportedWorkspace(BuildSystem.Bazel));

    JButton button = new JButton("...");
    button.addActionListener(action -> this.chooseDirectory());
    int buttonSize = this.directoryField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP,
            getIconComponent(),
            new JLabel("Workspace:"),
            this.directoryField,
            button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
  }

  @Override
  public WorkspacePathResolver getWorkspacePathResolver() {
    return new WorkspacePathResolverImpl(getWorkspaceRoot());
  }

  @Override
  public String getOptionName() {
    return "use-existing-bazel-workspace";
  }

  @Override
  public String getOptionText() {
    return "Use existing bazel workspace";
  }

  private static boolean isWorkspaceRoot(File file) {
    return BuildSystemProvider.getWorkspaceRootProvider(BuildSystem.Bazel).isWorkspaceRoot(file);
  }

  private static boolean isWorkspaceRoot(VirtualFile file) {
    return isWorkspaceRoot(new File(file.getPath()));
  }

  @Override
  public BuildSystem getBuildSystemForWorkspace() {
    return BuildSystem.Bazel;
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
  public File getVcsRoot() {
    String directory = getDirectory();
    if (!vcsRootCache.containsKey(directory)) {
      vcsRootCache.put(directory, doGetVcsRoot(new File(directory)));
    }
    return vcsRootCache.get(directory);
  }

  @Nullable
  private static File doGetVcsRoot(File file) {
    for (VcsRootChecker rootChecker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensions()) {
      for (File root = file; root != null; root = root.getParentFile()) {
        if (rootChecker.isRoot(root.getPath())) {
          return root;
        }
      }
    }
    return null;
  }

  @Override
  public boolean allowProjectDataInVcsRoot() {
    File vcsRoot = getVcsRoot();
    return vcsRoot != null && !vcsRoot.getPath().equals(getDirectory());
  }

  @Override
  public File getFileBrowserRoot() {
    return new File(getDirectory());
  }

  @Override
  public String getWorkspaceName() {
    File workspaceRoot = new File(getDirectory());
    return workspaceRoot.getName();
  }

  @Override
  @Nullable
  public String getBranchName() {
    return null;
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
    if (!isWorkspaceRoot(workspaceRootFile)) {
      return BlazeValidationResult.failure(
          "Invalid workspace root: choose a bazel workspace directory "
              + "(containing a WORKSPACE file)");
    }
    return BlazeValidationResult.success();
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
        }.withHideIgnored(false)
            .withTitle("Select Workspace Root")
            .withDescription("Select the directory of the workspace you want to use.")
            .withFileFilter(UseExistingBazelWorkspaceOption::isWorkspaceRoot);
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

  private static JComponent getIconComponent() {
    JLabel iconPanel =
        new JLabel(IconLoader.getIconSnapshot(BlazeIcons.BazelLeaf)) {
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
    UiUtil.setPreferredWidth(iconPanel, 16);
    return iconPanel;
  }
}
