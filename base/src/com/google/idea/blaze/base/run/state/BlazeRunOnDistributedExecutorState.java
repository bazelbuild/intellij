/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.state;

import com.google.idea.blaze.base.run.DistributedExecutorSupport;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.components.JBCheckBox;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jdom.Element;

/**
 * Provides an option to run blaze/bazel on a distributed executor, if available. If unchecked, we
 * fall back to whatever the default is.
 */
public class BlazeRunOnDistributedExecutorState implements RunConfigurationState {

  private static final String RUN_ON_DISTRIBUTED_EXECUTOR_ATTR =
      "blaze-run-on-distributed-executor";

  @Nullable private final DistributedExecutorSupport executorInfo;

  public boolean runOnDistributedExecutor;

  BlazeRunOnDistributedExecutorState(BuildSystem buildSystem) {
    executorInfo = DistributedExecutorSupport.getAvailableExecutor(buildSystem);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String string = element.getAttributeValue(RUN_ON_DISTRIBUTED_EXECUTOR_ATTR);
    if (string != null) {
      runOnDistributedExecutor = Boolean.parseBoolean(string);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (executorInfo != null && runOnDistributedExecutor) {
      element.setAttribute(
          RUN_ON_DISTRIBUTED_EXECUTOR_ATTR, Boolean.toString(runOnDistributedExecutor));
    } else {
      element.removeAttribute(RUN_ON_DISTRIBUTED_EXECUTOR_ATTR);
    }
  }

  @Override
  public RunOnExecutorStateEditor getEditor(Project project) {
    return new RunOnExecutorStateEditor();
  }

  /** Editor for {@link BlazeRunOnDistributedExecutorState} */
  class RunOnExecutorStateEditor implements RunConfigurationStateEditor {

    private final String executorName =
        executorInfo != null ? executorInfo.executorName() : "distributed executor";
    private final JBCheckBox checkBox = new JBCheckBox("Run on " + executorName);
    private final JLabel warning =
        new JLabel("Warning: test UI integration is not available when running on " + executorName);

    private boolean componentVisible = executorInfo != null;
    private boolean isTest = false;

    private RunOnExecutorStateEditor() {
      warning.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
      checkBox.addItemListener(e -> setVisibility());
      setVisibility();
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeRunOnDistributedExecutorState state = (BlazeRunOnDistributedExecutorState) genericState;
      checkBox.setSelected(state.runOnDistributedExecutor);
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeRunOnDistributedExecutorState state = (BlazeRunOnDistributedExecutorState) genericState;
      if (checkBox.isVisible()) {
        state.runOnDistributedExecutor = checkBox.isSelected();
      }
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(checkBox, warning);
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      checkBox.setEnabled(enabled);
    }

    void updateVisibility(boolean isTest) {
      this.componentVisible = executorInfo != null;
      this.isTest = isTest;
      setVisibility();
    }

    private void setVisibility() {
      warning.setVisible(componentVisible && isTest && checkBox.isSelected());
      checkBox.setVisible(componentVisible);
    }
  }
}
