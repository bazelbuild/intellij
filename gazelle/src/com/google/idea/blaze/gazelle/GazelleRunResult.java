package com.google.idea.blaze.gazelle;

/**
 * Enum to differentiate whether a gazelle run succeeded, failed, and/or had errors.
 */
public enum GazelleRunResult {
  // Note that this will only happen when Bazel itself fails or when we pass
  // `-strict` to gazelle, or when the gazelle target can't be built.
  FAILED_TO_RUN,

  RAN_WITH_ERRORS,
  SUCCESS,
}
