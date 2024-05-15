package com.google.idea.blaze.base.run.state;


import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/** Unit tests for {@link EnvironmentVariablesState}. */
@RunWith(JUnit4.class)
public class EnvironmentVariablesStateTest extends BlazeTestCase {

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
