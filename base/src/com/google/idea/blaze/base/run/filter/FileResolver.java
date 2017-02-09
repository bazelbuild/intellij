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
package com.google.idea.blaze.base.run.filter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

/** Parses file strings in blaze/bazel output. */
public interface FileResolver {

  ExtensionPointName<FileResolver> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FileStringParser");

  /**
   * Iterates through all available {@link FileResolver}s, returning the first successful result.
   */
  static VirtualFile resolve(Project project, String fileString) {
    for (FileResolver parser : EP_NAME.getExtensions()) {
      VirtualFile result = parser.resolveToFile(project, fileString);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  VirtualFile resolveToFile(Project project, String fileString);
}
