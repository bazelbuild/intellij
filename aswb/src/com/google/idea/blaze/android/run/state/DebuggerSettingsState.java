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
package com.google.idea.blaze.android.run.state;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import org.jdom.Element;

/** State for android debugger settings. */
public class DebuggerSettingsState implements RunConfigurationState {
  private static final String NATIVE_DEBUG_ATTR = "blaze-native-debug";
  private static final String DEBUGGER_STATES_TAG = "android-debugger-states";

  private final Map<String, AndroidDebuggerState> androidDebuggerStates;
  private boolean nativeDebuggingEnabled;

  public DebuggerSettingsState(boolean nativeDebuggingEnabled, List<AndroidDebugger> debuggers) {
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;

    androidDebuggerStates = Maps.newHashMap();
    for (AndroidDebugger androidDebugger : debuggers) {
      this.androidDebuggerStates.put(androidDebugger.getId(), androidDebugger.createState());
    }
  }

  public boolean isNativeDebuggingEnabled() {
    return nativeDebuggingEnabled;
  }

  public void setNativeDebuggingEnabled(boolean enabled) {
    nativeDebuggingEnabled = enabled;
  }

  /**
   * Returns the persisted state of the AndroidDebugger corresponding to the given ID, if available.
   * Note that any modifications to {@link AndroidDebuggerState}s returned by this function will be
   * persisted in this state and saved between IDE sessions.
   *
   * @param debuggerId The unique ID of the debugger to find persisted state for (i.e. {@link
   *     com.android.tools.idea.run.editor.AndroidDebugger#getId()})
   */
  @Nullable
  public AndroidDebuggerState getDebuggerStateById(String debuggerId) {
    return androidDebuggerStates.get(debuggerId);
  }

  @Override
  public void readExternal(Element element) {
    setNativeDebuggingEnabled(Boolean.parseBoolean(element.getAttributeValue(NATIVE_DEBUG_ATTR)));

    Element debuggerStatesElement = element.getChild(DEBUGGER_STATES_TAG);
    if (debuggerStatesElement != null) {
      for (Map.Entry<String, AndroidDebuggerState> entry : androidDebuggerStates.entrySet()) {
        Element optionElement = debuggerStatesElement.getChild(entry.getKey());
        if (optionElement != null) {
          entry.getValue().readExternal(optionElement);
        }
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));

    element.removeChildren(DEBUGGER_STATES_TAG);
    Element debuggerStatesElement = new Element(DEBUGGER_STATES_TAG);
    for (Map.Entry<String, AndroidDebuggerState> entry : androidDebuggerStates.entrySet()) {
      Element optionElement = new Element(entry.getKey());
      debuggerStatesElement.addContent(optionElement);
      entry.getValue().writeExternal(optionElement);
    }
    element.addContent(debuggerStatesElement);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new DebuggerSettingsStateEditor();
  }

  /** Component for editing user editable debugger options. */
  public static class DebuggerSettingsStateEditor implements RunConfigurationStateEditor {
    private final JCheckBox enableNativeDebuggingCheckBox;

    DebuggerSettingsStateEditor() {
      enableNativeDebuggingCheckBox = new JCheckBox("Enable native debugging", false);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      DebuggerSettingsState state = (DebuggerSettingsState) genericState;
      enableNativeDebuggingCheckBox.setSelected(state.isNativeDebuggingEnabled());
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      DebuggerSettingsState state = (DebuggerSettingsState) genericState;
      state.setNativeDebuggingEnabled(enableNativeDebuggingCheckBox.isSelected());
    }

    @Override
    public JComponent createComponent() {
      if (NdkSupport.NDK_SUPPORT.getValue()) {
        return UiUtil.createBox(enableNativeDebuggingCheckBox);
      }
      return UiUtil.createBox();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      enableNativeDebuggingCheckBox.setEnabled(enabled);
    }
  }
}
