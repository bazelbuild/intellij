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
package com.google.idea.blaze.base.ide;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.IncorrectOperationException;
import java.awt.GridBagLayout;
import java.io.File;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

class NewBlazePackageDialog extends DialogWrapper {
  private static final Logger logger = Logger.getInstance(NewBlazePackageDialog.class);

  private final Project project;
  private final PsiDirectory parentDirectory;

  @Nullable private Label newRule;
  @Nullable private Kind newRuleKind;

  private static final int UI_INDENT_LEVEL = 0;
  private static final int TEXT_FIELD_LENGTH = 40;
  private final JPanel component = new JPanel(new GridBagLayout());
  private final JBLabel packageLabel = new JBLabel("Package name:");
  private final JBTextField packageNameField = new JBTextField(TEXT_FIELD_LENGTH);
  private final NewRuleUI newRuleUI = new NewRuleUI(TEXT_FIELD_LENGTH);

  public NewBlazePackageDialog(Project project, PsiDirectory currentDirectory) {
    super(project);
    this.project = project;
    this.parentDirectory = currentDirectory;

    initializeUI();
  }

  private void initializeUI() {
    component.add(packageLabel);
    component.add(packageNameField, UiUtil.getFillLineConstraints(UI_INDENT_LEVEL));
    newRuleUI.fillUI(component, UI_INDENT_LEVEL);
    newRuleUI.syncRuleNameTo(packageNameField);
    UiUtil.fillBottom(component);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return component;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return packageNameField;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String packageName = packageNameField.getText();
    if (packageName == null) {
      return new ValidationInfo("Internal error, package was null");
    }
    if (packageName.length() == 0) {
      return new ValidationInfo(
          IdeBundle.message("error.name.should.be.specified"), packageNameField);
    }
    String error = Label.validatePackagePath(packageName);
    if (error != null) {
      return new ValidationInfo(error, packageNameField);
    }
    return newRuleUI.validate();
  }

  @Override
  protected void doOKAction() {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    logger.assertTrue(parentDirectory.getVirtualFile().isInLocalFileSystem());
    File parentDirectoryFile = new File(parentDirectory.getVirtualFile().getPath());
    String newPackageName = packageNameField.getText();
    File newPackageDirectory = new File(parentDirectoryFile, newPackageName);
    WorkspacePath newPackagePath = workspaceRoot.workspacePathFor(newPackageDirectory);

    TargetName newTargetName = newRuleUI.getRuleName();
    Label newRule = Label.create(newPackagePath, newTargetName);
    Kind ruleKind = newRuleUI.getSelectedRuleKind();
    try {
      parentDirectory.checkCreateSubdirectory(newPackageName);
    } catch (IncorrectOperationException ex) {
      showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage()));
      // do not close the dialog
      return;
    }
    this.newRule = newRule;
    this.newRuleKind = ruleKind;
    super.doOKAction();
  }

  private void showErrorDialog(String message) {
    String title = CommonBundle.getErrorTitle();
    Icon icon = Messages.getErrorIcon();
    Messages.showMessageDialog(component, message, title, icon);
  }

  @Nullable
  public Label getNewRule() {
    return newRule;
  }

  @Nullable
  public Kind getNewRuleKind() {
    return newRuleKind;
  }
}
