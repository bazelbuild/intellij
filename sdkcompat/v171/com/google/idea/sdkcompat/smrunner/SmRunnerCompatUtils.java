package com.google.idea.sdkcompat.smrunner;

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import javax.annotation.Nullable;

/** Handles SM-runner methods which have changed between our supported versions. */
public class SmRunnerCompatUtils {

  public static TestFailedEvent getTestFailedEvent(
      String name, @Nullable String message, @Nullable String content, long duration) {
    return new TestFailedEvent(
        name, null, message, content, true, null, null, null, null, false, false, duration);
  }
}
