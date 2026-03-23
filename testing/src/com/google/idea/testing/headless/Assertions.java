/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
