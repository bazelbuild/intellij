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
package com.google.idea.blaze.java.run.hotswap;

import com.google.idea.blaze.base.plugin.BlazeActionRemover;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.debugger.actions.HotSwapAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/** Overrides the built-in hotswap action for Blaze projects */
public class BlazeHotSwapAction extends AnAction {

  private static final Logger logger = Logger.getInstance(BlazeHotSwapAction.class);
  private static final String ACTION_ID = "Hotswap";

  static class Initializer extends ApplicationComponent.Adapter {
    @Override
    public void initComponent() {
      AnAction delegate = ActionManager.getInstance().getAction(ACTION_ID);
      if (delegate == null) {
        logger.warn("No built-in hotswap action found.");
        delegate = new HotSwapAction();
      }
      BlazeActionRemover.replaceAction(ACTION_ID, new BlazeHotSwapAction(delegate));
    }
  }

  private final AnAction delegate;

  private BlazeHotSwapAction(AnAction delegate) {
    super(
        delegate.getTemplatePresentation().getTextWithMnemonic(),
        delegate.getTemplatePresentation().getDescription(),
        delegate.getTemplatePresentation().getIcon());
    this.delegate = delegate;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!isBlazeProject(e)) {
      delegate.actionPerformed(e);
      return;
    }
    BlazeHotSwapManager.reloadChangedClasses(e.getProject());
  }

  @Override
  public void update(AnActionEvent e) {
    if (!isBlazeProject(e)) {
      delegate.update(e);
      return;
    }
    boolean canHotSwap =
        HotSwapUtils.enableHotSwapping.getValue()
            && BlazeHotSwapManager.findHotSwappableBlazeDebuggerSession(e.getProject()) != null;
    e.getPresentation().setEnabled(canHotSwap);
  }

  private static boolean isBlazeProject(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && Blaze.isBlazeProject(project);
  }
}
