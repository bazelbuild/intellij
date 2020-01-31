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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.BlazeCommandState;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** State specific to the android binary run configuration. */
public final class BlazeAndroidBinaryRunConfigurationState
    extends BlazeCommandRunConfigurationCommonState {
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final AndroidBinaryConfigState androidBinaryConfigState;

  public BlazeAndroidBinaryRunConfigurationState(BuildSystem buildSystem) {
    super(buildSystem);
    commonState = new BlazeAndroidRunConfigurationCommonState(buildSystem.getName(), false);
    androidBinaryConfigState = new AndroidBinaryConfigState();
  }

  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(blazeBinary, commonState, androidBinaryConfigState);
  }

  /** blaze flag state stored in {@link BlazeCommandRunConfigurationCommonState}. */
  @Override
  public RunConfigurationFlagsState getBlazeFlagsState() {
    return commonState.getBlazeFlagsState();
  }

  /** exe flag state stored in {@link BlazeCommandRunConfigurationCommonState}. */
  @Override
  public RunConfigurationFlagsState getExeFlagsState() {
    return commonState.getExeFlagsState();
  }

  /** Always return {@link BlazeCommandName.BUILD} for android_binary */
  @Override
  public BlazeCommandState getCommandState() {
    BlazeCommandState commandState = new BlazeCommandState();
    commandState.setCommand(BlazeCommandName.BUILD);
    return commandState;
  }

  public ImmutableList<String> getExpandedBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext context) {
    return ImmutableList.<String>builder()
        .addAll(BlazeFlags.blazeFlags(project, projectViewSet, command, context))
        .addAll(commonState.getNativeDebuggerFlags())
        .build();
  }

  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  public AndroidBinaryConfigState getAndroidBinaryConfigState() {
    return androidBinaryConfigState;
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public List<ValidationError> validate(@Nullable AndroidFacet facet) {
    return commonState.validate(facet);
  }
}
