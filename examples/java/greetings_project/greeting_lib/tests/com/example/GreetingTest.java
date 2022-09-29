package com.example;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class GreetingTest {
   @Test
    public void testGreetNoNames() {
        List<String> got = Greeting.getGreetings("greetings", Collections.emptyList());
       assertThat(got).isEmpty();
    }

    @Test
    public void testGreetOneName() {
        List<String> got = Greeting.getGreetings("greetings", Collections.singletonList("name"));
        assertThat(got).containsExactly("greetings name!");
    }

    @Test
    public void testGreetMultipleNames() {
        List<String> got = Greeting.getGreetings("greetings", Arrays.asList("name1", "name2"));
        assertThat(got).containsExactly("greetings name1!", "greetings name2!");
    }
}