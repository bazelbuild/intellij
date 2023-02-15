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
package com.google.idea.blaze.base.run.state;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTextField;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jdom.Element;

/** State for a Blaze binary to run configurations with. */
public final class BlazeWorkspaceState implements RunConfigurationState {
  private static final String BLAZE_WORKSPACE_ATTR = "blaze-workspace";

  @Nullable private String blazeWorkspace;

  public BlazeWorkspaceState() {}

  @Nullable
  public String getBlazeWorkspace() {
    return blazeWorkspace;
  }

  public void setBlazeWorkspace(@Nullable String blazeWorkspace) {
    this.blazeWorkspace = blazeWorkspace;
  }

  @Override
  public void readExternal(Element element) {
    blazeWorkspace = element.getAttributeValue(BLAZE_WORKSPACE_ATTR);
  }

  @Override
  public void writeExternal(Element element) {
    if (!Strings.isNullOrEmpty(blazeWorkspace)) {
      element.setAttribute(BLAZE_WORKSPACE_ATTR, blazeWorkspace);
    } else {
      element.removeAttribute(BLAZE_WORKSPACE_ATTR);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeWorkspaceStateEditor(project);
  }

  private static class BlazeWorkspaceStateEditor implements RunConfigurationStateEditor {
    private final String buildSystemName;

    private final JBTextField blazeWorkspaceField = new JBTextField(1);

    BlazeWorkspaceStateEditor(Project project) {
      buildSystemName = Blaze.buildSystemName(project);
      blazeWorkspaceField.getEmptyText().setText("(Same as current project)");
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      blazeWorkspaceField.setEnabled(enabled);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeWorkspaceState state = (BlazeWorkspaceState) genericState;
      blazeWorkspaceField.setText(Strings.nullToEmpty(state.getBlazeWorkspace()));
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeWorkspaceState state = (BlazeWorkspaceState) genericState;
      state.setBlazeWorkspace(Strings.emptyToNull(blazeWorkspaceField.getText()));
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(new JLabel(buildSystemName + " workspace:"), blazeWorkspaceField);
    }
  }
}
