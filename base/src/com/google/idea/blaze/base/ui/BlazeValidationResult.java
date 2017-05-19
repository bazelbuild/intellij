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

import javax.annotation.Nullable;

/** Pair of (success, validation error) */
public class BlazeValidationResult {
  public final boolean success;
  @Nullable public final BlazeValidationError error;

  private static final BlazeValidationResult SUCCESS = new BlazeValidationResult(true, null);

  private BlazeValidationResult(boolean success, @Nullable BlazeValidationError error) {
    this.success = success;
    this.error = error;
  }

  public static BlazeValidationResult success() {
    return SUCCESS;
  }

  public static BlazeValidationResult failure(BlazeValidationError error) {
    return new BlazeValidationResult(false, error);
  }

  public static BlazeValidationResult failure(String error) {
    return failure(new BlazeValidationError(error));
  }
}
