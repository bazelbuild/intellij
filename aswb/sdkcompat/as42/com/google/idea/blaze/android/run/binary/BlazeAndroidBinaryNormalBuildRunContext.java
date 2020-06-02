/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat for #api4.0 */
public class BlazeAndroidBinaryNormalBuildRunContext
    extends BlazeAndroidBinaryNormalBuildRunContextBase {
  BlazeAndroidBinaryNormalBuildRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags) {
    super(project, facet, runConfiguration, env, configState, label, blazeFlags);
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public DebugConnectorTask getDebuggerTask(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      Set<String> packageIds)
      throws ExecutionException {
    return androidDebugger.getConnectDebuggerTask(
        env,
        null,
        packageIds,
        facet,
        androidDebuggerState,
        runConfiguration.getType().getId(),
        null);
  }
}
