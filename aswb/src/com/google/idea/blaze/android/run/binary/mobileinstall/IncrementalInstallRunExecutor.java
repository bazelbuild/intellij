/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.executors.DefaultRunExecutor;
import icons.BlazeAndroidIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * Executor for running blaze mobile-install --incremental and then launching the current run
 * configuration. This executor adds a launch button that is only enabled for mobile-install run
 * configurations.
 */
public class IncrementalInstallRunExecutor extends DefaultRunExecutor
    implements IncrementalInstallExecutor {
  public static final String EXECUTOR_ID = "blaze.incremental.install.run";

  @NotNull
  @Override
  public Icon getIcon() {
    return BlazeAndroidIcons.MobileInstallRun;
  }

  @NotNull
  @Override
  public String getActionName() {
    return Blaze.guessBuildSystemName() + " incremental install and run";
  }

  @Override
  public String getContextActionId() {
    return "IncrementalInstallRunClass";
  }

  @NotNull
  @Override
  public String getStartActionText() {
    return Blaze.guessBuildSystemName() + " incremental install and run";
  }

  @Override
  public String getDescription() {
    return Blaze.guessBuildSystemName().toLowerCase() + "mobile-install --incremental, run";
  }

  @NotNull
  @Override
  public String getId() {
    return EXECUTOR_ID;
  }
}
