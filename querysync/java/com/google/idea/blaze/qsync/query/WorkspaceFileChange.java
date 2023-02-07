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
package com.google.idea.blaze.qsync.query;

import java.nio.file.Path;

/** Represents an edit to a file in the user's workspace. */
public final class WorkspaceFileChange {

  /** Type of change that affected the file. */
  public enum Operation {
    DELETE,
    ADD,
    MODIFY,
  }

  public final Operation operation;
  public final Path workspaceRelativePath;

  public WorkspaceFileChange(Operation operation, Path workspaceRelativePath) {
    this.operation = operation;
    this.workspaceRelativePath = workspaceRelativePath;
  }
}
