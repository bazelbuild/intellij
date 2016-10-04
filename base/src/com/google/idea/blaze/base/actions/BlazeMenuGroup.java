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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** The "Blaze" menu group, only visible in blaze mode */
public class BlazeMenuGroup extends DefaultActionGroup {
  @Override
  public final void update(AnActionEvent e) {
    if (!isBlazeProject(e)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
    e.getPresentation().setText(Blaze.buildSystemName(e.getProject()));
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static boolean isBlazeProject(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    return project != null && Blaze.isBlazeProject(project);
  }
}
