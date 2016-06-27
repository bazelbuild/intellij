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
package com.google.idea.blaze.base.wizard;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.WorkspaceRootProvider;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Some convenience methods regarding your import source.
 */
public class ImportSource {
  static boolean isBuildFile(@NotNull File file) {
    return file.getName().equals("BUILD");
  }

  static boolean canImport(@NotNull File file) {
    WorkspaceRootProvider helper = BuildSystemProvider.getWorkspaceRootProvider(BuildSystem.Blaze);
    if (helper.isWorkspaceRoot(file)) {
      return true;
    }
    if (ProjectViewStorageManager.isProjectViewFile(file.getName()) || isBuildFile(file)) {
      if (helper.isInWorkspace(file)) {
        return true;
      }
    }
    return false;
  }

  public static boolean canImport(@NotNull VirtualFile file) {
    return canImport(new File(file.getPath()));
  }
}
