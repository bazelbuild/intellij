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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetConfigurable;
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.state.DeployTargetSettingsStateTest.StubDeployTargetProvider.StubState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeployTargetSettingsState}. */
@RunWith(JUnit4.class)
public class DeployTargetSettingsStateTest {

  @Test
  public void testDeployTargetStateModificationsRetained() {
    DeployTargetProvider<? extends DeployTargetState> provider = new StubDeployTargetProvider();
    DeployTargetSettingsState state = new DeployTargetSettingsState(ImmutableList.of(provider));

    StubState mutableTargetState = (StubState) state.getTargetStateByProviderId(provider.getId());
    assertThat(mutableTargetState).isNotNull();
    mutableTargetState.intSetting = 1337;

    StubState sameAsPreviousTargetState =
        (StubState) state.getTargetStateByProviderId(provider.getId());
    assertThat(sameAsPreviousTargetState).isNotNull();
    assertThat(sameAsPreviousTargetState.intSetting).isEqualTo(1337);
  }

  @Test
  public void testDeployTargetSettingsRetainedAfterReserialization() {
    Element element = new Element("test_element");

    // Saving phase.
    DeployTargetProvider<? extends DeployTargetState> provider = new StubDeployTargetProvider();
    DeployTargetSettingsState state = new DeployTargetSettingsState(ImmutableList.of(provider));
    StubState targetState = (StubState) state.getTargetStateByProviderId(provider.getId());
    assertThat(targetState).isNotNull();
    targetState.intSetting = 1337;
    state.writeExternal(element);

    // Restoring phase.
    DeployTargetProvider<? extends DeployTargetState> secondProvider =
        new StubDeployTargetProvider();
    DeployTargetSettingsState secondState =
        new DeployTargetSettingsState(ImmutableList.of(secondProvider));
    secondState.readExternal(element);
    StubState restoredTargetState =
        (StubState) secondState.getTargetStateByProviderId(provider.getId());
    assertThat(restoredTargetState).isNotNull();
    assertThat(restoredTargetState.intSetting).isEqualTo(1337);
  }

  /** Stub deploy target provider with a single int setting in it's target state. */
  public static final class StubDeployTargetProvider extends DeployTargetProvider<StubState> {
    @NotNull
    @Override
    public String getId() {
      return "stub_deploy_target_provider";
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "Stub Deploy Target Provider";
    }

    @NotNull
    @Override
    public StubState createState() {
      return new StubState();
    }

    @Override
    public DeployTargetConfigurable<StubState> createConfigurable(
        @NotNull Project project,
        @NotNull Disposable disposable,
        @NotNull DeployTargetConfigurableContext deployTargetConfigurableContext) {
      throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public DeployTarget<StubState> getDeployTarget() {
      throw new UnsupportedOperationException("unimplemented");
    }

    /** Deploy target state with a single integer field. */
    public static final class StubState extends DeployTargetState {
      public int intSetting = 10;
    }
  }
}
