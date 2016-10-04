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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;

/**
 * A simplified, Blaze-specific variant of {@link
 * org.jetbrains.android.run.AndroidRunConfigurationEditor}.
 */
public class BlazeAndroidRunConfigurationCommonStateEditor {
  private final Project project;
  private final JTextArea userFlagsField;
  private final JCheckBox enableNativeDebuggingCheckBox;

  public BlazeAndroidRunConfigurationCommonStateEditor(Project project) {
    this.project = project;

    userFlagsField = new JTextArea(3 /* rows */, 50 /* columns */);
    userFlagsField.setToolTipText("e.g. --config=android_arm");
    enableNativeDebuggingCheckBox = new JCheckBox("Enable native debugging", false);
  }

  public void resetEditorFrom(BlazeAndroidRunConfigurationCommonState runConfigurationState) {
    userFlagsField.setText(ParametersListUtil.join(runConfigurationState.getUserFlags()));
    enableNativeDebuggingCheckBox.setSelected(runConfigurationState.isNativeDebuggingEnabled());
  }

  public void applyEditorTo(BlazeAndroidRunConfigurationCommonState runConfigurationState) {
    List<String> userFlags =
        ParametersListUtil.parse(Strings.nullToEmpty(userFlagsField.getText()));
    runConfigurationState.setUserFlags(userFlags);
    runConfigurationState.setNativeDebuggingEnabled(enableNativeDebuggingCheckBox.isSelected());
  }

  public List<JComponent> getComponents() {
    List<JComponent> result =
        Lists.newArrayList(
            new JLabel(String.format("Custom %s build flags:", Blaze.buildSystemName(project))),
            userFlagsField);
    if (NdkSupport.NDK_SUPPORT.getValue()) {
      result.add(enableNativeDebuggingCheckBox);
    }
    return result;
  }
}
