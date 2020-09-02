/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

/**
 * Extension of {@link NativeAndroidDebugger} with the following key differences compared to {@link
 * NativeAndroidDebugger}.
 *
 * <ul>
 *   <li>Sets blaze working directory for source file resolution. See {@link
 *       #createRunnerAndConfigurationSettings}.
 *   <li>Overloads default {@link #createState()} method to set the working directory.
 * </ul>
 */
public class BlazeNativeAndroidDebuggerBase extends NativeAndroidDebugger {
  public static final String ID = Blaze.defaultBuildSystemName() + "Native";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return "Native Only";
  }

  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }

  /**
   * Returns a new {@link NativeAndroidDebuggerState}.
   *
   * <p>Warning: Do not use this method, use {@link #createState(Project)} instead whenever
   * possible.
   *
   * <p>This overload of {@link #createState} cannot correctly initialize it's working directory as
   * well as working directory related flags correctly. An instance to the calling {@link Project}
   * is needed to initialize the working directory because the working directory is different for
   * each project. See implementation of {@link #createState(Project)} for more details.
   */
  @Override
  public NativeAndroidDebuggerState createState() {
    return super.createState();
  }

  /** Returns a state object with working directory and related flags initialized. */
  public NativeAndroidDebuggerState createState(Project project) {
    NativeAndroidDebuggerState nativeState = super.createState();
    String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
    nativeState.setWorkingDir(workingDirPath);

    // "/proc/self/cwd" used by linux built binaries isn't valid on MacOS.
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

    return nativeState;
  }
}
