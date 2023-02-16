package com.example.junit4;

import org.junit.Test;
import com.example.Greeting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GreetingTest {
   @Test
    public void testGreetNoNames() {
       List<String> got = Greeting.getGreetings("greetings", Collections.emptyList());
       assertEquals(0, got.size());
   }

    @Test
    public void testGreetOneName() {
        List<String> got = Greeting.getGreetings("greetings", Collections.singletonList("name"));
        assertEquals(1, got.size());
        assertEquals("greetings name!", got.get(0));
   }

    @Test
    public void testGreetMultipleNames() {
        List<String> got =
                Greeting.getGreetings("greetings", Arrays.asList("name1", "name2", "name3"));
        assertEquals(3, got.size());
        assertEquals("greetings name1!", got.get(0));
        assertEquals("greetings name2!", got.get(1));
        assertEquals("greetings name3!", got.get(2));
    }
}