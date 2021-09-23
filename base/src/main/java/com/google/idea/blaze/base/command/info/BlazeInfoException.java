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
package com.google.idea.blaze.base.command.info;

import javax.annotation.concurrent.Immutable;

/** Exception occuring during blaze info */
@Immutable
public final class BlazeInfoException extends Exception {
  private final int exitCode;
  private final String stdout;

  public BlazeInfoException(int exitCode, String stdout) {
    this.exitCode = exitCode;
    this.stdout = stdout;
  }

  @Override
  public String getMessage() {
    return "blaze info failed with exit code: " + exitCode;
  }

  public String getStdout() {
    return stdout;
  }
}
