package com.example;

import java.util.ArrayList;
import java.util.List;

public class Greeting {
    public static List<String> getGreetings(String greetingWord, List<String> names) {
        List <String> result = new ArrayList<>();
        for (String name : names) {
            result.add(String.format("%s %s!", greetingWord, name));
        }
       return result;
    }
}