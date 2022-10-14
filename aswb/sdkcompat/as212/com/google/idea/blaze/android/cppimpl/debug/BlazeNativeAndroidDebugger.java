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
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * API compat of {@link BlazeNativeAndroidDebuggerBase} with the following additions:
 *
 * <ul>
 *   <li>Creates a run-config setting using {@link BlazeAndroidNativeAttachConfiguration} instead of
 *       {@link AndroidNativeAttachConfiguration} to override counterproductive validations.
 * </ul>
 *
 * #api4.0
 */
public class BlazeNativeAndroidDebugger extends BlazeNativeAndroidDebuggerBase {
  @NotNull
  @Override
  protected RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(
      @NotNull Project project,
      @NotNull Module module,
      @NotNull Client client,
      @Nullable AndroidDebuggerState inputState) {
    String runConfigurationName =
        String.format(
            "%s %s Debugger (%d)",
            Blaze.getBuildSystemName(project).getName(),
            getDisplayName(),
            client.getClientData().getPid());
    RunnerAndConfigurationSettings runSettings =
        RunManager.getInstance(project)
            .createConfiguration(
                runConfigurationName, new BlazeAndroidNativeAttachConfigurationType.Factory());
    BlazeAndroidNativeAttachConfiguration configuration =
        (BlazeAndroidNativeAttachConfiguration) runSettings.getConfiguration();
    configuration.setClient(client);
    configuration.getAndroidDebuggerContext().setDebuggerType(getId());
    configuration.getConfigurationModule().setModule(module);
    configuration.setConsoleProvider(getConsoleProvider());

    // TODO(b/145707569): Copy debugger settings from inputState to state. See
    // NativeAndroidDebugger.
    AndroidDebuggerState state =
        configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    if (state instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) state;
      nativeState.setWorkingDir(WorkspaceRoot.fromProject(project).directory().getPath());
    }
    return runSettings;
  }
}
