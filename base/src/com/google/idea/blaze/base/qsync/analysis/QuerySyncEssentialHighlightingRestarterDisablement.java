package com.google.idea.blaze.base.qsync.analysis;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.sdkcompat.querysync.EssentialHighlightingRestarterDisablementAdapter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class QuerySyncEssentialHighlightingRestarterDisablement implements EssentialHighlightingRestarterDisablementAdapter {

  @Override
  public boolean shouldBeDisabledForProject(@NotNull Project project) {
    return Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC;
  }
}
