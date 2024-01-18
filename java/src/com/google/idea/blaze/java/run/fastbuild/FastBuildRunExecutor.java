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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import icons.BlazeIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.Icon;

/** An executor for debugging fast builds. */
public final class FastBuildRunExecutor extends DefaultRunExecutor {

  public static final String ID = "BlazeFastRun";
  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  public @NotNull String getStartActionText(@NotNull String configurationName) {
    if(configurationName.isEmpty()){
      return getStartActionText();
    }
    return String.format("%s: '%s'", getStartActionText(), shortenNameIfNeeded(configurationName));
  }

  @Override
  public @NotNull String getStartActionText() {
    return "Fast Run";
  }

  @Override
  public String getDescription() {
    return "Run without Blaze";
  }

  @Nullable
  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getActionName() {
    return "BlazeFastRun";
  }

  @Override
  public String getContextActionId() {
    return "BlazeFastRunContext";
  }

  @Override
  public Icon getIcon() {
    return new LayeredIcon(AllIcons.Actions.Execute, BlazeIcons.LightningOverlay);
  }

  @Override
  public Icon getDisabledIcon() {
    return new LayeredIcon(AllIcons.Process.Stop, BlazeIcons.LightningOverlay);
  }

  @Override
  public boolean isApplicable(Project project) {
    return Blaze.isBlazeProject(project);
  }
}
