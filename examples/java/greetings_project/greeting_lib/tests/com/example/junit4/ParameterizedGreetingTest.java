package com.example.junit4;

import com.example.Greeting;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ParameterizedGreetingTest {
    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameters(name = "Test name {0}")
    public static String[] names() {
        return new String[]{"Henry", "Roberta"};
    }

    @Test
    public void testGreetOneName() {
        List<String> got = Greeting.getGreetings("greetings", Collections.singletonList(name));
        assertEquals(1, got.size());
        String expected = "greetings " + name + "!";
        assertEquals(expected, got.get(0));
    }
}
