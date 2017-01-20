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
import com.android.tools.idea.run.editor.AndroidDebugger;
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
  private static final String DEPLOY_TARGET_STATES_TAG = "android-deploy-target-states";
  private static final String DEBUGGER_STATES_TAG = "android-debugger-states";
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

    Element deployTargetStatesElement = element.getChild(DEPLOY_TARGET_STATES_TAG);
    if (deployTargetStatesElement != null) {
      deployTargetManager.readExternal(deployTargetStatesElement);
    } else {
      // TODO Introduced in 1.12, remove in 1.14.
      // This was for migrating the state to a child element.
      deployTargetManager.readExternal(element);
    }

    Element debuggerStatesElement = element.getChild(DEBUGGER_STATES_TAG);
    if (debuggerStatesElement != null) {
      debuggerManager.readExternal(debuggerStatesElement);
    } else {
      // TODO Introduced in 1.12, remove in 1.14.
      // This was for migrating the state to a child element.
      debuggerManager.readExternal(element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    userFlags.writeExternal(element);
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));

    removeOldManagerState(element);

    element.removeChildren(DEPLOY_TARGET_STATES_TAG);
    Element deployTargetStatesElement = new Element(DEPLOY_TARGET_STATES_TAG);
    deployTargetManager.writeExternal(deployTargetStatesElement);
    element.addContent(deployTargetStatesElement);

    element.removeChildren(DEBUGGER_STATES_TAG);
    Element debuggerStatesElement = new Element(DEBUGGER_STATES_TAG);
    debuggerManager.writeExternal(debuggerStatesElement);
    element.addContent(debuggerStatesElement);
  }

  // TODO Introduced in 1.12, remove in 1.14. This was for migrating state
  // and cleaning up mass amounts of duplicate state caused by never removing these elements before.
  private void removeOldManagerState(Element element) {
    // This is safe because we know only BlazeAndroidRunConfigurationDeployTargetManager
    // directly wrote option elements (via DefaultJDOMExternalizer.writeExternal) to our root.
    element.removeChildren("option");
    // BlazeAndroidRunConfigurationDebuggerManager, meanwhile, nested its state in
    // child elements named after the AndroidDebugger extension IDs.
    for (AndroidDebugger<?> debugger : AndroidDebugger.EP_NAME.getExtensions()) {
      element.removeChildren(debugger.getId());
    }
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

    @Override
    public void setComponentEnabled(boolean enabled) {
      userFlagsEditor.setComponentEnabled(enabled);
      enableNativeDebuggingCheckBox.setEnabled(enabled);
    }
  }
}
