package com.google.idea.testing.headless;

import javax.annotation.Nullable;

public class Assertions {

  public static void abort(@Nullable String message, @Nullable Exception cause) {
    throw new AssertionError(message, cause);
  }

  public static void abort(@Nullable String message) {
    abort(message, null);
  }

  public static void abort() {
    abort(null);
  }
}
