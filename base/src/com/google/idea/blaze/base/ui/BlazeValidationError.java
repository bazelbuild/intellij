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
package com.google.idea.blaze.base.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Collection;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An error occuring during a blaze validation */
@Immutable
public final class BlazeValidationError {

  @NotNull private final String error;

  public BlazeValidationError(@NotNull String validationFailure) {
    this.error = validationFailure;
  }

  @NotNull
  public String getError() {
    return error;
  }

  public static void collect(
      @Nullable Collection<BlazeValidationError> errors, @NotNull BlazeValidationError error) {
    if (errors != null) {
      errors.add(error);
    }
  }

  public static void throwError(@NotNull Collection<BlazeValidationError> errors)
      throws IllegalArgumentException {
    BlazeValidationError error = !errors.isEmpty() ? errors.iterator().next() : null;
    String errorMessage = error != null ? error.getError() : "Unknown validation error";
    throw new IllegalArgumentException(errorMessage);
  }

  /**
   * Shows an error dialog.
   *
   * @return true if there are no errors
   */
  public static boolean verify(
      @NotNull Project project,
      @NotNull String title,
      @NotNull Collection<BlazeValidationError> errors) {
    if (!errors.isEmpty()) {
      BlazeValidationError error = errors.iterator().next();
      Messages.showErrorDialog(project, error.getError(), title);
      return false;
    }
    return true;
  }
}
