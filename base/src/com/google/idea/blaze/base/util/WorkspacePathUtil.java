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
package com.google.idea.blaze.base.util;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.util.io.FileUtil;
import java.util.Collection;

/** Removes any duplicates or overlapping directories */
public class WorkspacePathUtil {

  /** Returns whether the given workspace path is a child of any workspace path. */
  public static boolean isUnderAnyWorkspacePath(
      Collection<WorkspacePath> ancestors, WorkspacePath child) {
    return ancestors
        .stream()
        .anyMatch(
            importRoot ->
                FileUtil.isAncestor(importRoot.relativePath(), child.relativePath(), false));
  }

  /** Removes any duplicates or overlapping directories */
  public static ImmutableSet<WorkspacePath> calculateMinimalWorkspacePaths(
      Collection<WorkspacePath> workspacePaths) {
    ImmutableSet.Builder<WorkspacePath> minimalWorkspacePaths = ImmutableSet.builder();
    for (WorkspacePath directory : workspacePaths) {
      boolean ok = true;
      for (WorkspacePath otherDirectory : workspacePaths) {
        if (directory.equals(otherDirectory)) {
          continue;
        }
        if (FileUtil.isAncestor(otherDirectory.relativePath(), directory.relativePath(), true)) {
          ok = false;
          break;
        }
      }
      if (ok) {
        minimalWorkspacePaths.add(directory);
      }
    }
    return minimalWorkspacePaths.build();
  }
}
