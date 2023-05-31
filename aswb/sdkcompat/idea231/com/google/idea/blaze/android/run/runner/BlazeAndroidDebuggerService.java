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
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Provides android debuggers and debugger states for blaze projects. */
public interface BlazeAndroidDebuggerService {

  static BlazeAndroidDebuggerService getInstance(Project project) {
    return ServiceManager.getService(project, BlazeAndroidDebuggerService.class);
  }

  /**
   * Returns the standard debugger for non-native (Java) debugging.
   */
  AndroidDebugger<AndroidDebuggerState> getDebugger();

  /**
   * Default debugger service.
   */
  class DefaultDebuggerService implements BlazeAndroidDebuggerService {
    private final Project project;

    public DefaultDebuggerService(Project project) {
      this.project = project;
    }

    @Override
    public AndroidDebugger<AndroidDebuggerState> getDebugger() {
      return new AndroidJavaDebugger();
    }
  }
}
