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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.ndk.run.attach.AndroidNativeAttachConfiguration;
import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * {@link NativeAndroidDebugger} that sets working directory for source file resolution.
 *
 * <p>See {@link BlazeAndroidRunConfigurationDebuggerManager#getAndroidDebuggerState}.
 */
public class BlazeNativeAndroidDebugger extends NativeAndroidDebugger {
  public static final String ID = Blaze.defaultBuildSystemName() + "Native";

  @Override
  protected RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(
      Project project, Module module, Client client) {
    RunnerAndConfigurationSettings runSettings =
        super.createRunnerAndConfigurationSettings(project, module, client);
    AndroidNativeAttachConfiguration configuration =
        (AndroidNativeAttachConfiguration) runSettings.getConfiguration();
    AndroidDebuggerState state =
        configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    if (state instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) state;
      nativeState.setWorkingDir(WorkspaceRoot.fromProject(project).directory().getPath());
    }
    return runSettings;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Native Only";
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }
}
