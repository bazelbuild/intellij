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
package com.google.idea.blaze.base.settings.ui;

import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;

/** Helper methods for manipulating project views. */
final class ProjectViewHelper {
  private ProjectViewHelper() {}

  static void openProjectViewFile(Project project, ProjectViewFile projectViewFile) {
    File file = projectViewFile.projectViewFile;
    if (file != null) {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
      if (virtualFile != null) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      }
    }
  }
}
