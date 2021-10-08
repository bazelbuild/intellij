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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/** Factory for console window. */
public class BlazeConsoleToolWindowFactory implements DumbAware, ToolWindowFactory {

  public static final String ID = "Blaze Console";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    String title = Blaze.buildSystemName(project) + " Console";
    toolWindow.setTitle(title);
    toolWindow.setStripeTitle(title);
    BlazeConsoleView.getInstance(project).createToolWindowContent(toolWindow);
  }

  @Override
  public boolean shouldBeAvailable(Project project) {
    return BlazeConsoleExperimentManager.isBlazeConsoleV1Enabled();
  }
}
