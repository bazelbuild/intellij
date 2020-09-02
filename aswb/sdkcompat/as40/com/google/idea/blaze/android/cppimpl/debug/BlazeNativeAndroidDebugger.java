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
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * API compat of {@link BlazeNativeAndroidDebuggerBase} with the following additions:
 *
 * <ul>
 *   <li>Creates a run-config setting with working directory set from debugger state.
 * </ul>
 *
 * #api4.0
 */
public class BlazeNativeAndroidDebugger extends BlazeNativeAndroidDebuggerBase {
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
}
