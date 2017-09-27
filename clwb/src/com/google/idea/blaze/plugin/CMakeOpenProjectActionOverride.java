/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.jetbrains.cidr.cpp.OpenCPPProjectAction;
import com.jetbrains.cidr.cpp.cmake.CMakeProjectOpenProcessor;

/** Replace {@link OpenCPPProjectAction} with a version that supports non-CMake projects. */
public class CMakeOpenProjectActionOverride extends OpenCPPProjectAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(true, false);
    Project project = e.getData(CommonDataKeys.PROJECT);
    FileChooser.chooseFiles(
        descriptor,
        project,
        VfsUtil.getUserHomeDir(),
        files -> openProjectFile(project, files.get(0)));
  }

  private static VirtualFile getProjectFile(VirtualFile virtualFile) {
    return ReadAction.compute(
        () -> {
          VirtualFile cmakeFile = CMakeProjectOpenProcessor.findSupportedSubFile(virtualFile);
          if (cmakeFile != null) {
            return cmakeFile;
          }
          VirtualFile dotIdeaDir = ProjectKt.getProjectStoreDirectory(virtualFile);
          return dotIdeaDir != null ? dotIdeaDir.getParent() : null;
        });
  }

  private static void openProjectFile(Project project, VirtualFile file) {
    VirtualFile projectFile = getProjectFile(file);
    if (projectFile != null) {
      ProjectUtil.openOrImport(projectFile.getPath(), null, false);
      return;
    }
    String message = IdeBundle.message("error.dir.contains.no.project", file.getPresentableUrl());
    Messages.showInfoMessage(project, message, IdeBundle.message("title.cannot.open.project"));
  }
}
