package com.google.idea.testing.headless;

import static com.google.common.truth.Truth.assertWithMessage;

import java.nio.file.Files;
import java.nio.file.Path;
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

  public static void assertPathExists(Path path) {
    assertWithMessage(String.format("path does not exist: %s", path.toString())).that(Files.exists(path)).isTrue();
  }
}
