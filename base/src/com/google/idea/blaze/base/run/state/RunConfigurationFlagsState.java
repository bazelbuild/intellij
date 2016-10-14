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
package com.google.idea.blaze.base.run.state;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import org.jdom.Element;

/** State for a list of user-defined flags. */
public final class RunConfigurationFlagsState implements RunConfigurationState {

  private final String tag;
  private final String fieldLabel;

  private ImmutableList<String> flags = ImmutableList.of();

  public RunConfigurationFlagsState(String tag, String fieldLabel) {
    this.tag = tag;
    this.fieldLabel = fieldLabel;
  }

  public List<String> getFlags() {
    return flags;
  }

  public void setFlags(List<String> flags) {
    this.flags = ImmutableList.copyOf(flags);
  }

  @Override
  public void readExternal(Element element) {
    ImmutableList.Builder<String> flagsBuilder = ImmutableList.builder();
    for (Element e : element.getChildren(tag)) {
      String flag = e.getTextTrim();
      if (flag != null && !flag.isEmpty()) {
        flagsBuilder.add(flag);
      }
    }
    flags = flagsBuilder.build();
  }

  @Override
  public void writeExternal(Element element) {
    element.removeChildren(tag);
    for (String flag : flags) {
      Element child = new Element(tag);
      child.setText(flag);
      element.addContent(child);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new RunConfigurationFlagsStateEditor(fieldLabel);
  }

  private static class RunConfigurationFlagsStateEditor implements RunConfigurationStateEditor {

    private final JTextArea flagsField = new JTextArea(5, 1);
    private final String fieldLabel;

    RunConfigurationFlagsStateEditor(String fieldLabel) {
      this.fieldLabel = fieldLabel;
    }

    private static String makeFlagString(List<String> flags) {
      StringBuilder flagString = new StringBuilder();
      for (String flag : flags) {
        if (flagString.length() > 0) {
          flagString.append('\n');
        }
        if (flag.isEmpty() || flag.contains(" ") || flag.contains("|")) {
          flagString.append('"');
          flagString.append(flag);
          flagString.append('"');
        } else {
          flagString.append(flag);
        }
      }
      return flagString.toString();
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      RunConfigurationFlagsState state = (RunConfigurationFlagsState) genericState;
      // Normally we could just use ParametersListUtils.join, but that will only space-delimit args.
      flagsField.setText(makeFlagString(state.getFlags()));
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      RunConfigurationFlagsState state = (RunConfigurationFlagsState) genericState;
      state.setFlags(ParametersListUtil.parse(Strings.nullToEmpty(flagsField.getText())));
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(
          new JLabel(fieldLabel),
          new JScrollPane(
              flagsField,
              JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));
    }
  }
}
