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
package com.google.idea.blaze.base.console;

import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.intellij.codeEditor.printing.PrintAction;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import javax.annotation.Nullable;
import javax.swing.JComponent;

class BlazeConsoleView implements Disposable {

  private static final Class<?>[] IGNORED_CONSOLE_ACTION_TYPES = {
    PreviousOccurenceToolbarAction.class,
    NextOccurenceToolbarAction.class,
    ConsoleViewImpl.ClearAllAction.class,
    PrintAction.class
  };

  private final Project project;
  private final ConsoleViewImpl consoleView;

  private volatile Runnable stopHandler;

  public BlazeConsoleView(Project project) {
    this.project = project;
    consoleView = new ConsoleViewImpl(this.project, false);
    consoleView.addMessageFilter(new BlazeTargetFilter(project, false));
    Disposer.register(this, consoleView);
  }

  public static BlazeConsoleView getInstance(Project project) {
    return ServiceManager.getService(project, BlazeConsoleView.class);
  }

  public void setStopHandler(@Nullable Runnable stopHandler) {
    this.stopHandler = stopHandler;
  }

  private static boolean shouldIgnoreAction(AnAction action) {
    for (Class<?> actionType : IGNORED_CONSOLE_ACTION_TYPES) {
      if (actionType.isInstance(action)) {
        return true;
      }
    }
    return false;
  }

  public void createToolWindowContent(ToolWindow toolWindow) {
    // Create runner UI layout
    RunnerLayoutUi.Factory factory = RunnerLayoutUi.Factory.getInstance(project);
    RunnerLayoutUi layoutUi = factory.create("", "", "session", project);

    Content console =
        layoutUi.createContent(
            BlazeConsoleToolWindowFactory.ID, consoleView.getComponent(), "", null, null);
    console.setCloseable(false);
    layoutUi.addContent(console, 0, PlaceInGrid.right, false);

    // Adding actions
    DefaultActionGroup group = new DefaultActionGroup();
    layoutUi.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);

    AnAction[] consoleActions = consoleView.createConsoleActions();
    for (AnAction action : consoleActions) {
      if (!shouldIgnoreAction(action)) {
        group.add(action);
      }
    }
    group.add(new StopAction());

    JComponent layoutComponent = layoutUi.getComponent();

    //noinspection ConstantConditions
    Content content =
        ContentFactory.SERVICE.getInstance().createContent(layoutComponent, null, true);
    content.setCloseable(false);
    toolWindow.getContentManager().addContent(content);
  }

  public void clear() {
    consoleView.clear();
  }

  public void print(String text, ConsoleViewContentType contentType) {
    consoleView.print(text, contentType);
  }

  @Override
  public void dispose() {}

  private class StopAction extends DumbAwareAction {
    public StopAction() {
      super(IdeBundle.message("action.stop"), null, AllIcons.Actions.Suspend);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Runnable handler = stopHandler;
      if (handler != null) {
        handler.run();
        stopHandler = null;
      }
    }

    @Override
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      boolean isNowVisible = stopHandler != null;
      if (presentation.isEnabled() != isNowVisible) {
        presentation.setEnabled(isNowVisible);
      }
    }
  }
}
