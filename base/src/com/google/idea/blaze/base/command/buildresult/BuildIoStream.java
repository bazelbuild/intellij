/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import java.io.InputStream;

/** Encapsulates the output stream from a build, plus the exit code. */
public class BuildIoStream {

  private final int exitCode;
  private final InputStream ioStream;

  public BuildIoStream(int exitCode, InputStream ioStream) {
    this.exitCode = exitCode;
    this.ioStream = ioStream;
  }

  public int getExitCode() {
    return exitCode;
  }

  public InputStream getInputStream() {
    return ioStream;
  }
}
