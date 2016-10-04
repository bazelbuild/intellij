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
package com.google.idea.blaze.base.buildmodifier;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FileSystemModifierImpl extends FileSystemModifier {

  private final Project project;

  public FileSystemModifierImpl(@NotNull Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public File makeWorkspacePathDirs(@NotNull WorkspacePath workspacePath) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    File dir = workspaceRoot.fileForPath(workspacePath);
    boolean success = dir.mkdirs();
    return success ? dir : null;
  }

  @Nullable
  @Override
  public File createFile(@NotNull WorkspacePath parentDirectory, @NotNull String name) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    File dir = workspaceRoot.fileForPath(parentDirectory);
    File f = new File(dir, name);
    boolean success;
    try {
      success = f.createNewFile();
    } catch (IOException e) {
      success = false;
    }
    return success ? f : null;
  }
}
