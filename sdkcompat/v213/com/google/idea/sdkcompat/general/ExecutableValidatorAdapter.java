package com.google.idea.sdkcompat.general;

import com.intellij.execution.ExecutableValidator;
import com.intellij.openapi.project.Project;

/**
 * #api211: inline into HgExecutableValidator. Number of parameters for constructor has changed in
 * 2021.2.4
 */
public abstract class ExecutableValidatorAdapter extends ExecutableValidator {

  public ExecutableValidatorAdapter(
      Project project,
      String notificationErrorTitle,
      String notificationErrorDescription,
      String notificationSafeModeDescription) {
    super(
        project,
        notificationErrorTitle,
        notificationErrorDescription,
        notificationSafeModeDescription);
  }
}
