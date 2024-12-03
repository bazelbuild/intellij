package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.codeInsight.daemon.EssentialHighlightingRestarterDisablement;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class QuerySyncEssentialHighlightingRestarterDisablement implements EssentialHighlightingRestarterDisablement {

  @Override
  public boolean shouldBeDisabledForProject(@NotNull Project project) {
    return Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC;
  }
}
