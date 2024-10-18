package com.google.idea.blaze.clwb.base;

import org.junit.runners.model.Statement;

public class Statements {

  public static Statement message(String format, Object... args) {
    final var message = String.format(format, args);

    return new Statement() {
      @Override
      public void evaluate() {
        System.out.println(message);
      }
    };
  }

  public static Statement fail(String format, Object... args) {
    final var message = String.format(format, args);

    return new Statement() {
      @Override
      public void evaluate() {
        throw new AssertionError(message);
      }
    };
  }
}
