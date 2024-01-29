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
package com.google.idea.blaze.cpp;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

public interface SourceFileFinder {
  ExtensionPointName<SourceFileFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.cpp.SourceFileFinder");

  @Nullable
  VirtualFile getSourceFileForHeaderFile(Project project, VirtualFile headerFile);

  static VirtualFile findAndGetSourceFileForHeaderFile(Project project, VirtualFile headerFile) {
    if (EP_NAME.getExtensionList().size() > 1) {
      throw new IllegalStateException("More than 1 extension for " + EP_NAME.getName() + " is not supported");
    }

    SourceFileFinder finder = EP_NAME.getPoint().getExtensionList().stream().findFirst().orElse(null);
    if (finder != null) {
        return finder.getSourceFileForHeaderFile(project, headerFile);
    }

    return null;
  }
}
