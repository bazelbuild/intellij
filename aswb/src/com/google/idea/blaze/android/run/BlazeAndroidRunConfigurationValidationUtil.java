/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;

/**
 * Utility class for validating {@link BlazeAndroidRunConfigurationHandler}s. We collect
 * configuration errors rather than throwing to avoid missing fatal errors by exiting early for a
 * warning.
 */
public final class BlazeAndroidRunConfigurationValidationUtil {

  private static final String SYNC_FAILED_ERR_MSG =
      "Project state is invalid. Please sync and try your action again.";

  /**
   * Finds the top error, as determined by {@link ValidationError#compareTo(Object)}. If it is
   * fatal, it is thrown as a {@link RuntimeConfigurationError}; otherwise it is thrown as a {@link
   * RuntimeConfigurationWarning}. If no errors exist, nothing is thrown.
   */
  public static void throwTopConfigurationError(List<ValidationError> errors)
      throws RuntimeConfigurationException {
    if (errors.isEmpty()) {
      return;
    }
    // TODO: Do something with the extra error information? Error count?
    ValidationError topError = Ordering.natural().max(errors);
    if (topError.isFatal()) {
      throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
    }
    throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
  }

  public static List<ValidationError> validateModule(@Nullable Module module) {
    List<ValidationError> errors = Lists.newArrayList();
    if (module == null) {
      errors.add(
          ValidationError.fatal(
              "No workspace module found. Have you successfully synced your project?"));
      return errors;
    }
    if (AndroidPlatform.getInstance(module) == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    final Project project = module.getProject();
    if (AndroidProjectInfo.getInstance(project).requiredAndroidModelMissing()) {
      errors.add(ValidationError.fatal(SYNC_FAILED_ERR_MSG));
    }
    return errors;
  }

  public static void validateExecution(
      @Nullable Module module,
      @Nullable ProjectViewSet projectViewSet)
      throws ExecutionException {
    List<ValidationError> errors = Lists.newArrayList();
    errors.addAll(validateModule(module));
    if (projectViewSet == null) {
      errors.add(ValidationError.fatal("Could not load project view. Please resync project"));
    }

    if (errors.isEmpty()) {
      return;
    }
    ValidationError topError = Ordering.natural().max(errors);
    if (topError.isFatal()) {
      throw new ExecutionException(topError.getMessage());
    }
  }
}
