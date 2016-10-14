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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jdom.Element;

/** Helper class for managing composite state. */
public class RunConfigurationCompositeState implements RunConfigurationState {
  private final List<RunConfigurationState> states;

  public RunConfigurationCompositeState(List<RunConfigurationState> states) {
    this.states = states;
  }

  protected RunConfigurationCompositeState() {
    this.states = Lists.newArrayList();
  }

  protected void addStates(RunConfigurationState... states) {
    Collections.addAll(this.states, states);
  }

  @Override
  public final void readExternal(Element element) throws InvalidDataException {
    for (RunConfigurationState state : states) {
      state.readExternal(element);
    }
  }

  /** Updates the element with the handler's state. */
  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public final void writeExternal(Element element) throws WriteExternalException {
    for (RunConfigurationState state : states) {
      state.writeExternal(element);
    }
  }

  /** @return A {@link RunConfigurationStateEditor} for this state. */
  @Override
  public final RunConfigurationStateEditor getEditor(Project project) {
    return new RunConfigurationCompositeStateEditor(project, states);
  }

  private static class RunConfigurationCompositeStateEditor implements RunConfigurationStateEditor {
    List<RunConfigurationStateEditor> editors;

    public RunConfigurationCompositeStateEditor(
        Project project, List<RunConfigurationState> states) {
      editors = states.stream().map(state -> state.getEditor(project)).collect(Collectors.toList());
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      RunConfigurationCompositeState state = (RunConfigurationCompositeState) genericState;
      for (int i = 0; i < editors.size(); ++i) {
        editors.get(i).resetEditorFrom(state.states.get(i));
      }
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      RunConfigurationCompositeState state = (RunConfigurationCompositeState) genericState;
      for (int i = 0; i < editors.size(); ++i) {
        editors.get(i).applyEditorTo(state.states.get(i));
      }
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(
          editors
              .stream()
              .map(RunConfigurationStateEditor::createComponent)
              .collect(Collectors.toList()));
    }
  }
}
