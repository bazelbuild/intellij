package com.google.idea.blaze.clwb.base;

import com.google.idea.blaze.base.bazel.BazelVersion;
import java.util.Optional;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class BazelVersionRule implements TestRule {

  private final BazelVersion min;

  public BazelVersionRule(int major, int minor) {
    this.min = new BazelVersion(major, minor, 0);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    final var version = getBazelVersion();
    if (version.isEmpty()) {
      return Statements.fail("Could not read bazel version from BIT_BAZEL_VERSION");
    }

    if (version.get().isAtLeast(min)) {
      return base;
    } else {
      return Statements.message(
          "Test '%s' does not run on bazel version %s",
          description.getDisplayName(),
          version.get().toString()
      );
    }
  }

  public static Optional<BazelVersion> getBazelVersion() {
    final var bitBazelVersion = System.getenv("BIT_BAZEL_VERSION");
    if (bitBazelVersion == null) {
      return Optional.empty();
    }

    final var version = BazelVersion.parseVersion(bitBazelVersion);
    if (version.isAtLeast(999, 0, 0)) {
      return Optional.empty();
    }

    return Optional.of(version);
  }
}
