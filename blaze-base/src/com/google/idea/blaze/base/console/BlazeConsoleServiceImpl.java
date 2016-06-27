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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation for BlazeConsoleService
 */
public class BlazeConsoleServiceImpl implements BlazeConsoleService {
  @NotNull private final Project project;
  @NotNull private final BlazeConsoleView blazeConsoleView;

  BlazeConsoleServiceImpl(@NotNull Project project) {
    this.project = project;
    blazeConsoleView = BlazeConsoleView.getInstance(project);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    blazeConsoleView.print(text, contentType);
  }

  @Override
  public void clear() {
    blazeConsoleView.clear();
  }

  @Override
  public void setStopHandler(@Nullable Runnable runnable) {
    blazeConsoleView.setStopHandler(runnable);
  }

  @Override
  public void activateConsoleWindow() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
      BlazeConsoleToolWindowFactory.ID);
    if (toolWindow != null) {
      toolWindow.activate(null);
    }
  }
}
