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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.ui.BlazeValidationError;
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
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class NewBlazePackageDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(NewBlazePackageDialog.class);

  @NotNull private final Project project;
  @NotNull private final PsiDirectory parentDirectory;

  @Nullable private Label newRule;
  @Nullable private Kind newRuleKind;

  private static final int UI_INDENT_LEVEL = 0;
  private static final int TEXT_FIELD_LENGTH = 40;
  @NotNull private final JPanel component = new JPanel(new GridBagLayout());
  @NotNull private final JBLabel packageLabel = new JBLabel("Package name:");
  @NotNull private final JBTextField packageNameField = new JBTextField(TEXT_FIELD_LENGTH);
  @NotNull private final NewRuleUI newRuleUI = new NewRuleUI(TEXT_FIELD_LENGTH);

  public NewBlazePackageDialog(@NotNull Project project, @NotNull PsiDirectory currentDirectory) {
    super(project);
    this.project = project;
    this.parentDirectory = currentDirectory;

    initializeUI();
  }

  private void initializeUI() {
    component.add(packageLabel);
    component.add(packageNameField, UiUtil.getFillLineConstraints(UI_INDENT_LEVEL));
    newRuleUI.fillUI(component, UI_INDENT_LEVEL);
    UiUtil.fillBottom(component);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return component;
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
    List<BlazeValidationError> errors = Lists.newArrayList();
    if (!Label.validatePackagePath(packageName, errors)) {
      BlazeValidationError validationResult = errors.get(0);
      return new ValidationInfo(validationResult.getError(), packageNameField);
    }

    return newRuleUI.validate();
  }

  @Override
  protected void doOKAction() {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    LOG.assertTrue(parentDirectory.getVirtualFile().isInLocalFileSystem());
    File parentDirectoryFile = new File(parentDirectory.getVirtualFile().getPath());
    String newPackageName = packageNameField.getText();
    File newPackageDirectory = new File(parentDirectoryFile, newPackageName);
    WorkspacePath newPackagePath = workspaceRoot.workspacePathFor(newPackageDirectory);

    RuleName newRuleName = newRuleUI.getRuleName();
    Label newRule = new Label(newPackagePath, newRuleName);
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

  private void showErrorDialog(@NotNull String message) {
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
