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
package com.google.idea.blaze.base.plugin;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/** Wraps an action and makes it invisible for blaze-based projects. */
public class BlazeActionRemover extends AnAction {

  public static void hideAction(String actionId) {
    AnAction oldAction = ActionManager.getInstance().getAction(actionId);
    if (oldAction != null) {
      replaceAction(actionId, new BlazeActionRemover(oldAction));
    }
  }

  public static void replaceAction(String actionId, AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.unregisterAction(actionId);
    }
    actionManager.registerAction(actionId, newAction);
  }

  private final AnAction delegate;

  private BlazeActionRemover(AnAction delegate) {
    super(
        delegate.getTemplatePresentation().getTextWithMnemonic(),
        delegate.getTemplatePresentation().getDescription(),
        delegate.getTemplatePresentation().getIcon());
    this.delegate = delegate;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    delegate.actionPerformed(e);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project != null && Blaze.isBlazeProject(project)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    delegate.update(e);
  }
}
