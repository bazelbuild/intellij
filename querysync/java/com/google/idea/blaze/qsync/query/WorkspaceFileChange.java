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

import com.google.auto.value.AutoValue;
import java.nio.file.Path;

/** Represents an edit to a file in the users workspace. */
@AutoValue
public abstract class WorkspaceFileChange {

  /** Type of change that affected the file. */
  public enum Operation {
    DELETE,
    ADD,
    MODIFY,
  }

  public abstract Operation operation();

  public abstract Path workspaceRelativePath();

  public static Builder builder() {
    return new AutoValue_WorkspaceFileChange.Builder();
  }

  /** Builder for {@link WorkspaceFileChange}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder operation(Operation operation);

    public abstract Builder workspaceRelativePath(Path workspaceRelativePath);

    public abstract WorkspaceFileChange build();
  }
}
