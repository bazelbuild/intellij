package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// These tests are meant to be run with the run configurations in run_configurations
public class EnvVarsTest {
    @Test
    public void testEnvVars() {
        String hello = System.getenv("HELLO");
        assertEquals(hello, "world");
    }
}
