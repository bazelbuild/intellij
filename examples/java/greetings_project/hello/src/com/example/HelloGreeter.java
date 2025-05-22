package com.example;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HelloGreeter {
    public static void main(String[] args) {
        var name = System.getenv("FOO");

        throw new RuntimeException("Hello " + name);
    }
}