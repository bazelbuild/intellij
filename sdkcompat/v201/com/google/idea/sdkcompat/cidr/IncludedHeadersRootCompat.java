/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;

/** Compat methods for {@link IncludedHeadersRoot} */
public class IncludedHeadersRootCompat {
  /** {@link IncludedHeadersRoot} can only be created via a factory method starting with #api192 */
  public static IncludedHeadersRoot create(
      Project project, VirtualFile includedDir, boolean recursive, boolean userHeaders) {
    return IncludedHeadersRoot.create(project, includedDir, recursive, userHeaders);
  }
}
