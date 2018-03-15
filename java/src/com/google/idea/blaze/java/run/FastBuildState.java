/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.components.JBCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jdom.Element;

/** Whether or not to use the FastBuildService */
public class FastBuildState implements RunConfigurationState {

  private static final String ATTRIBUTE_TAG = "fast_run";

  private final boolean visible;

  private boolean useFastBuild = false;

  FastBuildState(boolean visible) {
    this.visible = visible;
  }

  public boolean useFastBuild() {
    return useFastBuild;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String value = element.getAttributeValue(ATTRIBUTE_TAG);
    if (value == null) {
      return;
    }
    useFastBuild = Boolean.valueOf(value);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (useFastBuild) {
      element.setAttribute(ATTRIBUTE_TAG, Boolean.toString(true));
    } else {
      element.removeAttribute(ATTRIBUTE_TAG);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    if (visible) {
      return new Editor();
    } else {
      return new EmptyEditor();
    }
  }

  private static class Editor implements RunConfigurationStateEditor {
    private final JBCheckBox enabledButton;

    public Editor() {
      enabledButton =
          new JBCheckBox("Fast Build (compiles without Blaze; build may not be accurate)");
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      enabledButton.setSelected(((FastBuildState) genericState).useFastBuild);
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      ((FastBuildState) genericState).useFastBuild = enabledButton.isSelected();
    }

    @Override
    public JComponent createComponent() {
      return enabledButton;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      this.enabledButton.setEnabled(enabled);
    }
  }

  private static class EmptyEditor implements RunConfigurationStateEditor {
    @Override
    public void resetEditorFrom(RunConfigurationState state) {}

    @Override
    public void applyEditorTo(RunConfigurationState state) {}

    @Override
    public JComponent createComponent() {
      return new JPanel();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {}
  }
}
