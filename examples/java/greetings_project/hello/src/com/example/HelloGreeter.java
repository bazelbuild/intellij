package com.example;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HelloGreeter {
    public static void main(String[] args) {
        List<String> names = Collections.singletonList("World");
        if (args.length > 0) {
            names = Arrays.asList(args);
        }

        List<String> greetings = Greeting.getGreetings("Hello", names);
        for (String greeting: greetings) {
            System.out.println(greeting);
        }
    }
}