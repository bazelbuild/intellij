/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.editor;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BazelEditorTabTitleProvider implements EditorTabTitleProvider {
  private static boolean requiresDecoration(Project project, String fileName) {
    return Blaze.getBuildSystemProvider(project).possibleBuildFileNames().contains(fileName);
  }
  @Override
  public @Nullable String getEditorTabTitle(@NotNull Project project,
      @NotNull VirtualFile virtualFile) {
    if (Blaze.getProjectType(project) == ProjectType.UNKNOWN) {
      return null;
    }

    var fileName = virtualFile.getName();
    if (!requiresDecoration(project, fileName)) {
      return null;
    }

    return BuildFile.getBuildFileString(project, virtualFile.getPath());
  }

  @Override
  public @Nullable String getEditorTabTooltipText(@NotNull Project project,
      @NotNull VirtualFile virtualFile) {
    return EditorTabTitleProvider.super.getEditorTabTooltipText(project, virtualFile);
  }
}
