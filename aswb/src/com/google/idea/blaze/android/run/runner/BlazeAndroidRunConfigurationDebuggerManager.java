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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.cppimpl.debug.BlazeAutoAndroidDebugger;
import com.google.idea.blaze.android.run.state.DebuggerSettingsState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.util.List;
import javax.annotation.Nullable;

/** Manages android debugger state for the run configurations. */
public final class BlazeAndroidRunConfigurationDebuggerManager {
  private final DebuggerSettingsState debuggerSettings;

  public BlazeAndroidRunConfigurationDebuggerManager(DebuggerSettingsState debuggerSettings) {
    this.debuggerSettings = debuggerSettings;
  }

  @Nullable
  public AndroidDebugger getAndroidDebugger() {
    String debuggerID = getDebuggerID();

    // Note: AndroidDebugger.EP_NAME includes native debugger(s).
    for (AndroidDebugger androidDebugger : AndroidDebugger.EP_NAME.getExtensions()) {
      if (androidDebugger.getId().equals(debuggerID)) {
        return androidDebugger;
      }
    }
    return null;
  }

  @Nullable
  public AndroidDebuggerState getAndroidDebuggerState(Project project) {
    AndroidDebugger debuggerToUse = getAndroidDebugger();
    if (debuggerToUse == null) {
      return null;
    }

    AndroidDebuggerState androidDebuggerState = debuggerToUse.createState();
    if (androidDebuggerState instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) androidDebuggerState;
      String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
      nativeState.setWorkingDir(workingDirPath);

      // b/151821788 "/proc/self/cwd" used by linux built binaries isn't valid on MacOS.
      if (SystemInfo.isMac) {
        String sourceMapToWorkspaceRootCommand =
            "settings set target.source-map /proc/self/cwd/ " + workingDirPath;
        ImmutableList<String> startupCommands =
            ImmutableList.<String>builder()
                .addAll(nativeState.getUserStartupCommands())
                .add(sourceMapToWorkspaceRootCommand)
                .build();
        nativeState.setUserStartupCommands(startupCommands);
      }
    }
    return androidDebuggerState;
  }

  public List<AndroidDebugger> getAndroidDebuggers(Project project) {
    List<AndroidDebugger> androidDebuggers = Lists.newArrayList();
    for (AndroidDebugger androidDebugger : AndroidDebugger.EP_NAME.getExtensions()) {
      if (androidDebugger.supportsProject(project)) {
        androidDebuggers.add(androidDebugger);
      }
    }
    return androidDebuggers;
  }

  private String getDebuggerID() {
    return debuggerSettings.isNativeDebuggingEnabled()
        ? BlazeAutoAndroidDebugger.ID
        : AndroidJavaDebugger.ID;
  }
}
