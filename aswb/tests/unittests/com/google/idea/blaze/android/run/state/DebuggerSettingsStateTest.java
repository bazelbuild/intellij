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

import com.android.ddmlib.Client;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerConfigurable;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import java.util.Set;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DebuggerSettingsState}. */
@RunWith(JUnit4.class)
public class DebuggerSettingsStateTest {

  @Test
  public void testNativeDebuggingOptionRetainedAfterEditorReset() {
    DebuggerSettingsState state = new DebuggerSettingsState(true, ImmutableList.of());

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.isNativeDebuggingEnabled()).isTrue();

    state.setNativeDebuggingEnabled(false);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.isNativeDebuggingEnabled()).isFalse();
  }

  @Test
  public void testDebuggerStatesRetainedAfterReserialization() {
    Element element = new Element("test_element");

    StubAndroidDebugger stubDebugger = new StubAndroidDebugger();
    DebuggerSettingsState state = new DebuggerSettingsState(true, ImmutableList.of(stubDebugger));
    AndroidDebuggerState androidDebuggerState = state.getDebuggerStateById(stubDebugger.getId());
    if (androidDebuggerState instanceof StubAndroidDebuggerState) {
      ((StubAndroidDebuggerState) androidDebuggerState).someState = 1337;
    } else {
      Assert.fail("AndroidDebuggerState is of inconsistent type.");
    }
    state.writeExternal(element);

    StubAndroidDebugger anotherStubDebugger = new StubAndroidDebugger();
    DebuggerSettingsState anotherState =
        new DebuggerSettingsState(false, ImmutableList.of(anotherStubDebugger));
    anotherState.readExternal(element);
    AndroidDebuggerState anotherAndroidDebuggerState =
        anotherState.getDebuggerStateById(anotherStubDebugger.getId());

    if (anotherAndroidDebuggerState instanceof StubAndroidDebuggerState) {
      assertThat(((StubAndroidDebuggerState) anotherAndroidDebuggerState).someState)
          .isEqualTo(1337);
    } else {
      Assert.fail("AndroidDebuggerState is of inconsistent type.");
    }
    assertThat(anotherState.isNativeDebuggingEnabled()).isTrue();
  }

  @Test
  public void testDebuggerStatesForUninitializedDebuggersAreNotLoaded() {
    Element element = new Element("test_element");

    StubAndroidDebugger stubDebugger = new StubAndroidDebugger();
    DebuggerSettingsState state = new DebuggerSettingsState(true, ImmutableList.of(stubDebugger));
    AndroidDebuggerState androidDebuggerState = state.getDebuggerStateById(stubDebugger.getId());
    if (androidDebuggerState instanceof StubAndroidDebuggerState) {
      ((StubAndroidDebuggerState) androidDebuggerState).someState = 1337;
    } else {
      Assert.fail("AndroidDebuggerState is of inconsistent type.");
    }
    state.writeExternal(element);

    // Make a new state with native debugger DISABLED and NO debugger states.
    DebuggerSettingsState anotherState = new DebuggerSettingsState(false, ImmutableList.of());
    anotherState.readExternal(element);
    AndroidDebuggerState anotherAndroidDebuggerState =
        anotherState.getDebuggerStateById(stubDebugger.getId());

    // The state should be null because the required debugger state wasn't initialized.
    assertThat(anotherAndroidDebuggerState).isNull();
    assertThat(anotherState.isNativeDebuggingEnabled()).isTrue();
  }

  private static class StubAndroidDebuggerState extends AndroidDebuggerState {
    // This needs to be public for XML serialization to work.
    public int someState = 0;
  }

  private static class StubAndroidDebugger implements AndroidDebugger {

    @NotNull
    @Override
    public String getId() {
      return "stub-android-debugger";
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "Stub Android Debugger";
    }

    @NotNull
    @Override
    public AndroidDebuggerState createState() {
      return new StubAndroidDebuggerState();
    }

    @NotNull
    @Override
    public AndroidDebuggerConfigurable createConfigurable(
        @NotNull RunConfiguration runConfiguration) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean supportsProject(@NotNull Project project) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void attachToClient(@NotNull Project project, @NotNull Client client) {
      throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    // @Override #api3.6
    public Set<XBreakpointType<?, ?>> getSupportedBreakpointTypes(
        @NotNull Project project, @NotNull AndroidVersion androidVersion) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean shouldBeDefault() {
      throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    @Override
    public String getAmStartOptions(
        @NotNull AndroidDebuggerState androidDebuggerState,
        @NotNull Project project,
        @NotNull AndroidVersion androidVersion) {
      throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    // @Override #api3.5
    public DebugConnectorTask getConnectDebuggerTask(
        @NotNull ExecutionEnvironment executionEnvironment,
        @Nullable AndroidVersion androidVersion,
        @NotNull Set set,
        @NotNull AndroidFacet androidFacet,
        @NotNull AndroidDebuggerState androidDebuggerState,
        @NotNull String s1,
        boolean b) {
      throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    // @Override #api3.5
    public DebugConnectorTask getConnectDebuggerTask(
        @NotNull ExecutionEnvironment executionEnvironment,
        @Nullable AndroidVersion androidVersion,
        @NotNull Set set,
        @NotNull AndroidFacet androidFacet,
        @NotNull AndroidDebuggerState androidDebuggerState,
        @NotNull String s1) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
