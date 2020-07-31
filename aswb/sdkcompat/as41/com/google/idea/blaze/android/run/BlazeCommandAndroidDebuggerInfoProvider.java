/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerInfoProvider;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Provider of blaze project compatible android debuggers. */
public class BlazeCommandAndroidDebuggerInfoProvider implements AndroidDebuggerInfoProvider {
  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null;
  }

  @Override
  public List<AndroidDebugger> getAndroidDebuggers(RunConfiguration configuration) {
    if (configuration instanceof BlazeCommandRunConfiguration) {
      BlazeAndroidBinaryRunConfigurationState state =
          ((BlazeCommandRunConfiguration) configuration)
              .getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);

      if (state != null) {
        return state
            .getCommonState()
            .getDebuggerManager()
            .getAndroidDebuggers(configuration.getProject());
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public AndroidDebugger getSelectedAndroidDebugger(RunConfiguration configuration) {
    if (configuration instanceof BlazeCommandRunConfiguration) {
      BlazeAndroidBinaryRunConfigurationState state =
          ((BlazeCommandRunConfiguration) configuration)
              .getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);
      if (state != null) {
        return state.getCommonState().getDebuggerManager().getAndroidDebugger();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public AndroidDebuggerState getSelectedAndroidDebuggerState(RunConfiguration configuration) {
    if (configuration instanceof BlazeCommandRunConfiguration) {
      BlazeAndroidBinaryRunConfigurationState state =
          ((BlazeCommandRunConfiguration) configuration)
              .getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);
      if (state != null) {
        BlazeAndroidRunConfigurationDebuggerManager debuggerManager =
            state.getCommonState().getDebuggerManager();
        return debuggerManager.getAndroidDebuggerState(configuration.getProject());
      }
    }
    return null;
  }
}
