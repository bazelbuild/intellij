/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui.problems;

import com.intellij.ide.actions.NextOccurenceToolbarAction; // NOTYPO
import com.intellij.ide.actions.PreviousOccurenceToolbarAction; // NOTYPO
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import java.awt.BorderLayout;
import javax.swing.JComponent;

/** A custom error tree view panel for Blaze invocation errors. */
class BlazeProblemsViewPanel extends NewErrorTreeViewPanel {

  BlazeProblemsViewPanel(Project project) {
    super(project, "reference.problems.tool.window", false, false, null);
    myTree.getEmptyText().setText("No problems found");
    add(createToolbarPanel(), BorderLayout.WEST);
  }

  /** A custom toolbar panel, without most of the irrelevant built-in items. */
  private JComponent createToolbarPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new PreviousOccurenceToolbarAction(this)); // NOTYPO
    group.add(new NextOccurenceToolbarAction(this)); // NOTYPO
    fillRightToolbarGroup(group);
    ActionToolbar toolbar =
        ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, group, false);
    return toolbar.getComponent();
  }

  @Override
  protected boolean shouldShowFirstErrorInEditor() {
    return false;
  }

  @Override
  protected boolean canHideWarnings() {
    return false;
  }
}
