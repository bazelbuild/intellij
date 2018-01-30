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

import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.GridBagLayout;
import java.io.File;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

class NewBlazeRuleDialog extends DialogWrapper {
  private static final int UI_INDENT = 0;
  private static final int TEXT_BOX_WIDTH = 40;

  private final Project project;
  private final VirtualFile buildFile;
  private final String buildSystemName;

  private JPanel component = new JPanel(new GridBagLayout());
  private final NewRuleUI newRuleUI = new NewRuleUI(TEXT_BOX_WIDTH);

  NewBlazeRuleDialog(Project project, VirtualFile buildFile) {
    super(project);
    this.project = project;
    this.buildFile = buildFile;
    this.buildSystemName = Blaze.buildSystemName(project);
    initComponent();
  }

  private void initComponent() {
    setTitle(String.format("Create a New %s Rule", buildSystemName));
    setOKButtonText("Create");
    setCancelButtonText("Cancel");

    newRuleUI.fillUI(component, UI_INDENT);
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
    return newRuleUI.validate();
  }

  @Override
  protected void doOKAction() {
    TargetName targetName = newRuleUI.getRuleName();
    Kind ruleKind = newRuleUI.getSelectedRuleKind();

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    WorkspacePath workspacePath =
        workspaceRoot.workspacePathFor(new File(buildFile.getParent().getPath()));
    Label newRule = Label.create(workspacePath, targetName);
    BuildFileModifier buildFileModifier = BuildFileModifier.getInstance();

    String commandName = String.format("Add %s %s rule '%s'", buildSystemName, ruleKind, newRule);

    boolean success =
        new WriteCommandAction<Boolean>(project, commandName) {
          @Override
          protected void run(@NotNull Result<Boolean> result) throws Throwable {
            LocalHistory localHistory = LocalHistory.getInstance();
            LocalHistoryAction action = localHistory.startAction(commandName);
            try {
              result.setResult(buildFileModifier.addRule(project, newRule, ruleKind));
            } finally {
              action.finish();
            }
          }
        }.execute().getResultObject();

    if (success) {
      super.doOKAction();
    } else {
      super.setErrorText(
          String.format("Could not create new rule, see %s Console for details", buildSystemName));
    }
  }
}
