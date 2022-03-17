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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import icons.BlazeIcons;

final class RerunFastBuildConfigurationWithBlazeAction extends AnAction {

  private final Project project;
  private final Label label;
  private final ExecutionEnvironment env;

  RerunFastBuildConfigurationWithBlazeAction(
      Project project, Label label, ExecutionEnvironment env) {
    super(
        "Rerun tests with clean build from " + getBuildSystem(project),
        "Rerun the tests after generating a new build with " + getBuildSystem(project),
        BlazeIcons.BlazeRerun);
    this.project = project;
    this.label = label;
    this.env = env;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FastBuildService.getInstance(project).resetBuild(label);
    ExecutionUtil.restart(env);
  }

  private static String getBuildSystem(Project project) {
    return Blaze.getBuildSystemName(project).getName();
  }
}
