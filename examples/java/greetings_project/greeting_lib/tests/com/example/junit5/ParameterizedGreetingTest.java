package com.example.junit5;

import com.example.Greeting;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParameterizedGreetingTest {
  @ParameterizedTest
  @ValueSource(strings = { "Henry", "Roberta" })
  public void testGreetOneName(String name) {
    List<String> got = Greeting.getGreetings("greetings", Collections.singletonList(name));
    assertEquals(1, got.size());
    String expected = "greetings " + name + "!";
    assertEquals(expected, got.get(0));
  }
}
