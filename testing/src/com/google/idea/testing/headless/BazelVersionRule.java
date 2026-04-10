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

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.intellij.util.system.OS;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class BazelVersionRule implements TestRule {

  private final @Nullable BazelVersion min;
  private final @Nullable BazelVersion max;
  private final @Nullable OS os;

  private BazelVersionRule(@Nullable OS os, @Nullable BazelVersion min, @Nullable BazelVersion max) {
    this.min = min;
    this.max = max;
    this.os = os;
  }

  public static BazelVersionRule min(@Nullable OS os, int major, int minor) {
    return new BazelVersionRule(os, new BazelVersion(major, minor, 0), null);
  }

  public static BazelVersionRule min(int major, int minor) {
    return min(null, major, minor);
  }

  public static BazelVersionRule max(@Nullable OS os, int major, int minor) {
    return new BazelVersionRule(os, null, new BazelVersion(major, minor, 0));
  }

  public static BazelVersionRule max(int major, int minor) {
    return max(null, major, minor);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    final var version = getBazelVersion();
    if (version.isEmpty()) {
      return Statements.fail("Could not read Bazel version from BIT_BAZEL_VERSION");
    }

    // check if the rule applies for the current OS
    if (os != null && !OS.CURRENT.equals(os)) {
      return base;
    }

    if (min != null && !version.get().isAtLeast(min)) {
      return Statements.message(
          "Test '%s' does not run on Bazel version %s (minimum: %s)",
          description.getDisplayName(),
          version.get().toString(),
          min.toString()
      );
    }

    if (max != null && !version.get().isAtMost(max)) {
      return Statements.message(
          "Test '%s' does not run on Bazel version %s (maximum: %s)",
          description.getDisplayName(),
          version.get().toString(),
          max.toString()
      );
    }

    return base;
  }

  public static Optional<BazelVersion> getBazelVersion() {
    final var bitBazelVersion = System.getenv("BIT_BAZEL_VERSION");
    if (bitBazelVersion == null) {
      return Optional.empty();
    }
    if (bitBazelVersion.equals("last_green")) {
      return Optional.of(BazelVersion.DEVELOPMENT);
    }

    final var version = BazelVersion.parseVersion(bitBazelVersion);
    if (version.isAtLeast(BazelVersion.DEVELOPMENT)) {
      return Optional.empty();
    }

    return Optional.of(version);
  }
}
