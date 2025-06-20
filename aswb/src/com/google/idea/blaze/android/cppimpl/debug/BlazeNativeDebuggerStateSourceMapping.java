/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

/** Maps source directory when attaching to a process */
public class BlazeNativeDebuggerStateSourceMapping {

  /** Registry key to enable or disable maping the working directory to /proc/self/cwd */
  public static final String MAP_WORKING_DIR = "bazel.cpp.debug.map_working_dir";

  /** LLDB command to map /proc/self/cwd to the project root */
  public static final String SOURCE_MAP_TO_WORKSPACE_ROOT_COMMAND = "settings append target.source-map /proc/self/cwd/ ";

  public static void addSourceMapping(
      @NotNull Project project, @NotNull NativeAndroidDebuggerState state) {
    // Source code is always relative to the workspace root in a blaze project.
    String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
    state.setWorkingDir(workingDirPath);

    ImmutableList.Builder <String> startupCommands =
        ImmutableList.<String>builder()
        .addAll(state.getUserStartupCommands());

        // Google specific logic for binaries built in their RBE system.
      if (Registry.is(MAP_WORKING_DIR, true)) {
        // Remote built binaries may use /proc/self/cwd to represent the working directory
        // so we manually map /proc/self/cwd to the workspace root.  We used to use
        // `plugin.symbol-file.dwarf.comp-dir-symlink-paths = "/proc/self/cwd"`
        // to automatically resolve this but it's no longer supported in newer versions of
        // LLDB.
        startupCommands.add(
              SOURCE_MAP_TO_WORKSPACE_ROOT_COMMAND + workingDirPath);
      }
      state.setUserStartupCommands(startupCommands.build());
  }
}
