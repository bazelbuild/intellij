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

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.ndk.run.editor.AutoAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.cppimpl.debug.BlazeAutoAndroidDebugger;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Provides android debuggers and debugger states for blaze projects. */
public interface BlazeAndroidDebuggerService {

  static BlazeAndroidDebuggerService getInstance(Project project) {
    return project.getService(BlazeAndroidDebuggerService.class);
  }

  /** Returns the standard debugger for non-native (Java) debugging. */
  AndroidDebugger<AndroidDebuggerState> getDebugger();

  /** Returns the standard debugger for native (C++) debugging. */
  AndroidDebugger<AutoAndroidDebuggerState> getNativeDebugger();

  /**
   * Performs additional necessary setup for native debugging, incorporating info from {@link
   * BlazeAndroidDeployInfo}.
   */
  void configureNativeDebugger(
      AndroidDebuggerState state, @Nullable BlazeAndroidDeployInfo deployInfo);

  /** Default debugger service. */
  class DefaultDebuggerService implements BlazeAndroidDebuggerService {
    private final Project project;

    public DefaultDebuggerService(Project project) {
      this.project = project;
    }

    @Override
    public AndroidDebugger<AndroidDebuggerState> getDebugger() {
      return new AndroidJavaDebugger();
    }

    @Override
    public AndroidDebugger<AutoAndroidDebuggerState> getNativeDebugger() {
      return new BlazeAutoAndroidDebugger();
    }

    @Override
    public void configureNativeDebugger(
        AndroidDebuggerState rawState, @Nullable BlazeAndroidDeployInfo deployInfo) {
      if (!isNdkPluginLoaded() && !(rawState instanceof AutoAndroidDebuggerState)) {
        return;
      }
      AutoAndroidDebuggerState state = (AutoAndroidDebuggerState) rawState;

      // Source code is always relative to the workspace root in a blaze project.
      String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
      state.setWorkingDir(workingDirPath);

      // Remote built binaries may use /proc/self/cwd to represent the working directory,
      // so we manually map /proc/self/cwd to the workspace root.  We used to use
      // `plugin.symbol-file.dwarf.comp-dir-symlink-paths = "/proc/self/cwd"`
      // to automatically resolve this, but it's no longer supported in newer versions of
      // LLDB.
      String sourceMapToWorkspaceRootCommand =
          "settings append target.source-map /proc/self/cwd/ " + workingDirPath;

      ImmutableList<String> startupCommands =
          ImmutableList.<String>builder()
              .addAll(state.getUserStartupCommands())
              .add(sourceMapToWorkspaceRootCommand)
              .build();
      state.setUserStartupCommands(startupCommands);

      // NDK plugin will pass symbol directories to LLDB as `settings append
      // target.exec-search-paths`.
      if (deployInfo != null) {
        state.setSymbolDirs(
            deployInfo.getSymbolFiles().stream()
                .map(symbol -> symbol.getParentFile().getAbsolutePath())
                .collect(ImmutableList.toImmutableList()));
      }
    }
  }

  static boolean isNdkPluginLoaded() {
    return PluginManagerCore.getLoadedPlugins().stream()
        .anyMatch(
            d -> d.isEnabled() && d.getPluginId().getIdString().equals("com.android.tools.ndk"));
  }
}
