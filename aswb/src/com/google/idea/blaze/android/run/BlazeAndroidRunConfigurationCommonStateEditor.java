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
import com.google.common.collect.Ordering;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.ComboWrapper;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.execution.ParametersListUtil;

import javax.swing.*;
import java.util.List;

/**
 * A simplified, Blaze-specific variant of
 * {@link org.jetbrains.android.run.AndroidRunConfigurationEditor}.
 */
public class BlazeAndroidRunConfigurationCommonStateEditor {
  private static final Ordering<RuleIdeInfo> ALPHABETICAL = Ordering.usingToString().onResultOf((ruleIdeInfo) -> ruleIdeInfo.label);

  private final Project project;
  private final Kind kind;
  private final ComboWrapper<RuleIdeInfo> ruleCombo;
  private final JTextArea userFlagsField;
  private final JCheckBox enableNativeDebuggingCheckBox;

  public BlazeAndroidRunConfigurationCommonStateEditor(
    Project project,
    Kind kind) {
    this.project = project;
    this.kind = kind;

    ruleCombo = ComboWrapper.create();
    List<RuleIdeInfo> rules = ALPHABETICAL.sortedCopy(RuleFinder.getInstance().rulesOfKinds(project, kind));
    ruleCombo.setItems(rules);
    ruleCombo.setRenderer(new ListCellRendererWrapper<RuleIdeInfo>() {
      @Override
      public void customize(JList list, RuleIdeInfo value, int index,
                            boolean selected, boolean hasFocus) {
        setText(value == null ? "" : value.label.toString());
      }
    });

    userFlagsField = new JTextArea(3 /* rows */, 50 /* columns */);
    userFlagsField.setToolTipText("e.g. --config=android_arm");
    enableNativeDebuggingCheckBox = new JCheckBox("Enable native debugging", false);
  }

  public void resetEditorFrom(BlazeAndroidRunConfigurationCommonState runConfigurationState) {
    Label target = runConfigurationState.getTarget();
    RuleIdeInfo rule = target != null ? RuleFinder.getInstance().ruleForTarget(project, target) : null;
    ruleCombo.setSelectedItem(rule);
    userFlagsField.setText(ParametersListUtil.join(runConfigurationState.getUserFlags()));
    enableNativeDebuggingCheckBox.setSelected(runConfigurationState.isNativeDebuggingEnabled());
  }

  public void applyEditorTo(BlazeAndroidRunConfigurationCommonState runConfigurationState)
    throws ConfigurationException {
    RuleIdeInfo rule = ruleCombo.getSelectedItem();
    Label target = rule != null ? rule.label : null;
    runConfigurationState.setTarget(target);
    List<String> userFlags = ParametersListUtil.parse(Strings.nullToEmpty(userFlagsField.getText()));
    runConfigurationState.setUserFlags(userFlags);
    runConfigurationState.setNativeDebuggingEnabled(enableNativeDebuggingCheckBox.isSelected());
  }

  public List<JComponent> getComponents() {
    List<JComponent> result = Lists.newArrayList(
      new JLabel(kind.toString() + " rule:"),
      ruleCombo.getCombo(),
      new JLabel(String.format("Custom %s build flags:", Blaze.buildSystemName(project))),
      userFlagsField
    );

    if (NdkSupport.NDK_SUPPORT.getValue()) {
       result.add(enableNativeDebuggingCheckBox);
    }
    return result;
  }
}
