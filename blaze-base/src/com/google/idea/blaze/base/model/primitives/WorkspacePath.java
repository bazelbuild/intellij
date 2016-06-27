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
package com.google.idea.blaze.base.model.primitives;

import com.google.idea.blaze.base.ui.BlazeValidationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.util.Collection;

/**
 * Represents a path relative to the workspace root. The path component separator is Blaze specific.
 * <p/>
 * A {@link WorkspacePath} is *not* necessarily a valid package name/path. The primary reason is
 * because it could represent a file and files don't have to follow the same conventions as package
 * names.
 */
@Immutable
public class WorkspacePath implements Serializable {
  public static final long serialVersionUID = 1L;

  /**
   * Silently returns null if this is not a valid workspace path.
   */
  @Nullable
  public static WorkspacePath createIfValid(String relativePath) {
    if (validate(relativePath)) {
      return new WorkspacePath(relativePath);
    }
    return null;
  }

  private static final char BLAZE_COMPONENT_SEPARATOR = '/';

  @NotNull
  private final String relativePath;

  /**
   * @param relativePath relative path that must use the Blaze specific separator char to separate
   *                     path components
   */
  public WorkspacePath(@NotNull String relativePath) {
    if (!validate(relativePath)) {
      throw new IllegalArgumentException("Invalid workspace path: " + relativePath);
    }
    this.relativePath = relativePath;
  }

  public WorkspacePath(@NotNull WorkspacePath parentPath, @NotNull String childPath) {
    this(parentPath.relativePath() + BLAZE_COMPONENT_SEPARATOR + childPath);
  }

  public static boolean validate(@NotNull String relativePath) {
    return validate(relativePath, null);
  }

  public static boolean validate(@NotNull String relativePath, @Nullable Collection<BlazeValidationError> errors) {
    if (relativePath.startsWith("/") ) {
      BlazeValidationError.collect(errors, new BlazeValidationError("Workspace path may not start with '/': " + relativePath));
      return false;
    }

    if (relativePath.endsWith("/") ) {
      BlazeValidationError.collect(errors, new BlazeValidationError("Workspace path may not end with '/': " + relativePath));
      return false;
    }

    if (relativePath.indexOf(':') >= 0) {
      BlazeValidationError.collect(errors, new BlazeValidationError("Workspace path may not contain ':': " + relativePath));
      return false;
    }

    return true;
  }

  public boolean isWorkspaceRoot() {
    return relativePath.isEmpty();
  }

  @Override
  public String toString() {
    return relativePath;
  }

  public String relativePath() {
    return relativePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    WorkspacePath that = (WorkspacePath)o;
    return relativePath.equals(that.relativePath);
  }

  @Override
  public int hashCode() {
    return relativePath.hashCode();
  }
}
