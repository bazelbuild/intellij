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
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.google.idea.blaze.android.cppimpl.debug.BlazeNativeAndroidDebugger;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDebuggerService;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Provider of blaze project compatible android debuggers. #api4.0 */
public class BlazeCommandAndroidDebuggerInfoProvider implements AndroidDebuggerInfoProvider {
  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null;
  }

  @Override
  public List<AndroidDebugger> getAndroidDebuggers(RunConfiguration configuration) {
    if (getCommonState(configuration) != null) {
      return Arrays.asList(new BlazeNativeAndroidDebugger(), new AndroidJavaDebugger());
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public AndroidDebugger getSelectedAndroidDebugger(RunConfiguration configuration) {
    // b/170159822 Always return java debugger because BlazeAutoAndroidDebugger doesn't work and
    //             users likely want the java debugger not the native debugger.
    return new AndroidJavaDebugger();
  }

  @Nullable
  @Override
  public AndroidDebuggerState getSelectedAndroidDebuggerState(RunConfiguration configuration) {
    AndroidDebugger debugger = getSelectedAndroidDebugger(configuration);
    if (debugger == null) {
      return null;
    }
    return BlazeAndroidDebuggerService.getInstance(configuration.getProject())
        .getDebuggerState(debugger);
  }

  private BlazeAndroidRunConfigurationCommonState getCommonState(RunConfiguration configuration) {
    if (!(configuration instanceof BlazeCommandRunConfiguration)) {
      return null;
    }
    BlazeCommandRunConfiguration blazeRunConfig = (BlazeCommandRunConfiguration) configuration;
    BlazeAndroidBinaryRunConfigurationState binaryState =
        blazeRunConfig.getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);
    if (binaryState != null) {
      return binaryState.getCommonState();
    }
    BlazeAndroidTestRunConfigurationState testState =
        blazeRunConfig.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (testState != null) {
      return binaryState.getCommonState();
    }
    return null;
  }
}
