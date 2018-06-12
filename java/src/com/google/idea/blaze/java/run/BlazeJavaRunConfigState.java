/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.DebugPortState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.intellij.openapi.project.Project;

/** A java-specific version of the common state, allowing the debug port to be customized. */
public class BlazeJavaRunConfigState extends BlazeCommandRunConfigurationCommonState {

  private static final int DEFAULT_DEBUG_PORT = 5005;

  private final DebugPortState debugPortState;

  // TODO(plumpy): when FastBuildService#enabled is removed, this can be @Nullable. Currently,
  // you'll get exceptions if the experiment value is changed and we use a @Nullable here.
  private final FastBuildState fastBuildState;

  BlazeJavaRunConfigState(BuildSystem buildSystem, Project project, Kind kind) {
    super(buildSystem);
    debugPortState = new DebugPortState(DEFAULT_DEBUG_PORT);
    fastBuildState =
        new FastBuildState(
            FastBuildService.enabled.getValue()
                && FastBuildService.getInstance(project).supportsFastBuilds(kind));
  }

  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(
        command, blazeFlags, exeFlags, debugPortState, fastBuildState, blazeBinary);
  }

  DebugPortState getDebugPortState() {
    return debugPortState;
  }

  public FastBuildState getFastBuildState() {
    return fastBuildState;
  }
}
