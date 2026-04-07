/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.run.state;


import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/** Unit tests for {@link EnvironmentVariablesState}. */
@RunWith(JUnit4.class)
public class EnvironmentVariablesStateTest extends BlazeIntegrationTestCase {

    @Test
    public void testSetEnvVarsReadWrite() {
        Map<String, String> env = ImmutableMap.of("HELLO", "world", "HI", "friends");
        EnvironmentVariablesState state = new EnvironmentVariablesState();

        assertThat(state.getData().getEnvs()).isEmpty();
        state.setEnvVars(env);

        RunConfigurationStateEditor editor = state.getEditor(null);
        editor.resetEditorFrom(state);
        editor.applyEditorTo(state);

        assertThat(state.getData().getEnvs()).isEqualTo(env);
        assertThat(state.asBlazeTestEnvFlags())
                .containsExactly("--test_env", "HELLO=world", "--test_env", "HI=friends")
                .inOrder();
    }

    @Test
    public void testAsBlazeTestFlags() {
        EnvironmentVariablesState state = new EnvironmentVariablesState();
        assertThat(state.asBlazeTestEnvFlags()).isEmpty();
        state.setEnvVars(ImmutableMap.of("HELLO", "world", "HI", "friends"));
        assertThat(state.asBlazeTestEnvFlags())
                .containsExactly("--test_env", "HELLO=world", "--test_env", "HI=friends")
                .inOrder();
    }
}
