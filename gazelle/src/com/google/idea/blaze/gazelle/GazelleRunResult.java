package com.google.idea.blaze.gazelle;

public enum GazelleRunResult {
  // Note that this will only happen when Bazel itself fails or when we pass
  // `-strict` to gazelle.
  FAILED_TO_RUN,

  RAN_WITH_ERRORS,
  SUCCESS,
}
