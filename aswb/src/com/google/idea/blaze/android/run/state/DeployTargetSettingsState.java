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

import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * Holds android deploy target settings. A persistent state object which aggregates the persisted
 * state of all the various {@link DeployTargetProvider}s that the IDE knows about.
 *
 * <p>Deploy target states are not user editable. Instead, they are managed by the users of this
 * class by obtaining the {@link DeployTargetState} instances and modifying them.
 */
public class DeployTargetSettingsState implements RunConfigurationState {
  private static final String DEPLOY_TARGET_STATES_TAG = "android-deploy-target-states";
  private final Map<String, DeployTargetState> deployTargetStatesByProviderId;

  /**
   * Initialize a default list of {@link DeployTargetState}s using a given list of {@link
   * DeployTargetProvider}s with one state per provider. These states are persisted between IDE
   * sessions and the defaults will most likely be overridden by persisted settings. Note: it is
   * still important to initialize the correct defaults, as the provider settings will only be read
   * if the default state exists.
   */
  public DeployTargetSettingsState(List<DeployTargetProvider> deployTargetProviders) {
    ImmutableMap.Builder<String, DeployTargetState> builder = ImmutableMap.builder();
    // Need to suppress unchecked conversion warnings due to third party code
    // not returning DeployTargetProviders with properly attached generic arguments.
    // @see DeployTargetProvider#getProviders()
    for (@SuppressWarnings("unchecked")
    DeployTargetProvider<? extends DeployTargetState> provider : deployTargetProviders) {
      builder.put(provider.getId(), provider.createState());
    }
    this.deployTargetStatesByProviderId = builder.build();
  }

  /**
   * Returns a mutable map of target provider IDs to their target settings. Changes to these
   * settings will be retained and saved through this {@link DeployTargetSettingsState}.
   */
  public Map<String, DeployTargetState> getTargetStatesByProviderId() {
    return deployTargetStatesByProviderId;
  }

  /**
   * Returns the target settings for a given provider. Changes to the settings will be retained and
   * saved through this {@link DeployTargetSettingsState}.
   */
  @Nullable
  public DeployTargetState getTargetStateByProviderId(String providerId) {
    return deployTargetStatesByProviderId.get(providerId);
  }

  @Override
  public void readExternal(Element element) {
    Element deployTargetStatesElement = element.getChild(DEPLOY_TARGET_STATES_TAG);
    if (deployTargetStatesElement != null) {
      for (DeployTargetState state : deployTargetStatesByProviderId.values()) {
        DefaultJDOMExternalizer.readExternal(state, deployTargetStatesElement);
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.removeChildren(DEPLOY_TARGET_STATES_TAG);
    Element deployTargetStatesElement = new Element(DEPLOY_TARGET_STATES_TAG);
    for (DeployTargetState state : deployTargetStatesByProviderId.values()) {
      DefaultJDOMExternalizer.writeExternal(state, deployTargetStatesElement);
    }
    element.addContent(deployTargetStatesElement);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlankEditor();
  }

  /** A blank UI component that doesn't edit anything. */
  public static class BlankEditor implements RunConfigurationStateEditor {
    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {}

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {}

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {}
  }
}
