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
