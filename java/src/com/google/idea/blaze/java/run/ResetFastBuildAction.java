/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import icons.BlazeIcons;
import javax.swing.Icon;

final class ResetFastBuildAction extends AnAction {

  private final Project project;
  private final FastBuildInfo fastBuildInfo;

  ResetFastBuildAction(Project project, FastBuildInfo fastBuildInfo) {
    super(
        "Reset build", "Discard the cached build (next build will be from scratch)", createIcon());
    this.project = project;
    this.fastBuildInfo = fastBuildInfo;
  }

  private static Icon createIcon() {
    return new LayeredIcon(BlazeIcons.Blaze, AllIcons.Nodes.ExcludedFromCompile);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FastBuildService.getInstance(project).resetBuild(fastBuildInfo.label());
  }
}
