/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import static com.google.idea.blaze.android.cppapi.NdkSupport.NDK_SUPPORT;

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDeployTargetManager;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.awt.Component;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;

/** A shared state class for run configurations targeting Blaze Android rules. */
public class BlazeAndroidRunConfigurationCommonState implements RunConfigurationState {
  private static final String USER_FLAG_TAG = "blaze-user-flag";
  private static final String NATIVE_DEBUG_ATTR = "blaze-native-debug";

  // We need to split "-c dbg" into two flags because we pass flags
  // as a list of strings to the command line executor and we need blaze
  // to see -c and dbg as two separate entities, not one.
  private static final ImmutableList<String> NATIVE_DEBUG_FLAGS =
      ImmutableList.of("--fission=no", "-c", "dbg");

  private final BlazeAndroidRunConfigurationDeployTargetManager deployTargetManager;
  private final BlazeAndroidRunConfigurationDebuggerManager debuggerManager;

  private final RunConfigurationFlagsState userFlags;
  private boolean nativeDebuggingEnabled = false;

  public BlazeAndroidRunConfigurationCommonState(String buildSystemName, boolean isAndroidTest) {
    this.deployTargetManager = new BlazeAndroidRunConfigurationDeployTargetManager(isAndroidTest);
    this.debuggerManager = new BlazeAndroidRunConfigurationDebuggerManager(this);
    this.userFlags =
        new RunConfigurationFlagsState(
            USER_FLAG_TAG, String.format("Custom %s build flags:", buildSystemName));
  }

  public BlazeAndroidRunConfigurationDeployTargetManager getDeployTargetManager() {
    return deployTargetManager;
  }

  public BlazeAndroidRunConfigurationDebuggerManager getDebuggerManager() {
    return debuggerManager;
  }

  public List<String> getUserFlags() {
    return userFlags.getFlags();
  }

  public void setUserFlags(List<String> userFlags) {
    this.userFlags.setFlags(userFlags);
  }

  public boolean isNativeDebuggingEnabled() {
    return nativeDebuggingEnabled && NDK_SUPPORT.getValue();
  }

  public void setNativeDebuggingEnabled(boolean nativeDebuggingEnabled) {
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
  }

  public ImmutableList<String> getBuildFlags(Project project, ProjectViewSet projectViewSet) {
    return ImmutableList.<String>builder()
        .addAll(BlazeFlags.buildFlags(project, projectViewSet))
        .addAll(getUserFlags())
        .addAll(getNativeDebuggerFlags())
        .build();
  }

  private ImmutableList<String> getNativeDebuggerFlags() {
    return isNativeDebuggingEnabled() ? NATIVE_DEBUG_FLAGS : ImmutableList.of();
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public List<ValidationError> validate(@Nullable AndroidFacet facet) {
    List<ValidationError> errors = Lists.newArrayList();
    // If facet is null, we can't validate the managers, but that's fine because
    // BlazeAndroidRunConfigurationValidationUtil.validateFacet will give a fatal error.
    if (facet != null) {
      errors.addAll(deployTargetManager.validate(facet));
      errors.addAll(debuggerManager.validate(facet));
    }
    return errors;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    userFlags.readExternal(element);
    setNativeDebuggingEnabled(Boolean.parseBoolean(element.getAttributeValue(NATIVE_DEBUG_ATTR)));

    deployTargetManager.readExternal(element);
    debuggerManager.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    userFlags.writeExternal(element);
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));

    deployTargetManager.writeExternal(element);
    debuggerManager.writeExternal(element);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeAndroidRunConfigurationCommonStateEditor(this, project);
  }

  private static class BlazeAndroidRunConfigurationCommonStateEditor
      implements RunConfigurationStateEditor {

    private final RunConfigurationStateEditor userFlagsEditor;
    private final JCheckBox enableNativeDebuggingCheckBox;

    BlazeAndroidRunConfigurationCommonStateEditor(
        BlazeAndroidRunConfigurationCommonState state, Project project) {
      userFlagsEditor = state.userFlags.getEditor(project);
      enableNativeDebuggingCheckBox = new JCheckBox("Enable native debugging", false);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeAndroidRunConfigurationCommonState state =
          (BlazeAndroidRunConfigurationCommonState) genericState;
      userFlagsEditor.resetEditorFrom(state.userFlags);
      enableNativeDebuggingCheckBox.setSelected(state.isNativeDebuggingEnabled());
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeAndroidRunConfigurationCommonState state =
          (BlazeAndroidRunConfigurationCommonState) genericState;
      userFlagsEditor.applyEditorTo(state.userFlags);
      state.setNativeDebuggingEnabled(enableNativeDebuggingCheckBox.isSelected());
    }

    @Override
    public JComponent createComponent() {
      List<Component> result = Lists.newArrayList(userFlagsEditor.createComponent());
      if (NdkSupport.NDK_SUPPORT.getValue()) {
        result.add(enableNativeDebuggingCheckBox);
      }
      return UiUtil.createBox(result);
    }
  }
}
