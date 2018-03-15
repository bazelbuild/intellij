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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RunConfigurationFlagsState}. */
@RunWith(JUnit4.class)
public class RunConfigurationFlagStateTest {

  @Test
  public void testEscapedQuotesRetainedAfterReserialization() {
    // previously, we were removing escape chars and quotes during ParametersListUtil.parse, then
    // not putting them back when converting back to a string.
    ImmutableList<String> flags = ImmutableList.of("--flag=\\\"Hello_world!\\\"", "--flag2");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);

    assertThat(state.getRawFlags()).isEqualTo(flags);
  }

  @Test
  public void testQuotesRetainedAfterReserialization() {
    ImmutableList<String> flags = ImmutableList.of("\"--flag=test\"");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);

    assertThat(state.getRawFlags()).isEqualTo(flags);
  }

  @Test
  public void testNormalFlagsAreNotMangled() {
    ImmutableList<String> flags =
        ImmutableList.of(
            "--test_sharding_strategy=disabled",
            "--test_strategy=local",
            "--experimental_show_artifacts",
            "--test_filter=com.google.idea.blaze.base.run.state.RunConfigurationFlagStateTest#",
            "--define=ij_product=intellij-latest");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);

    assertThat(state.getRawFlags()).isEqualTo(flags);
  }
}
