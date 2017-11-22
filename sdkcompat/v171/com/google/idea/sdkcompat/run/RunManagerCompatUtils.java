package com.google.idea.sdkcompat.run;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;

/** SDK compatibility bridge for {@link RunManager}. */
public class RunManagerCompatUtils {

  /**
   * Try to remove the configuration from RunManager's list. Returns false if unsuccessful (for
   * example, because there is no 'remove' method for this plugin API).
   */
  public static boolean removeConfiguration(
      RunManager manager, RunnerAndConfigurationSettings settings) {
    // RunManager#removeConfiguration not present in 2017.1 plugin API
    return false;
  }
}
