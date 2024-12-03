package com.google.idea.sdkcompat.querysync;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface EssentialHighlightingRestarterDisablementAdapter {

  /* api243 */
  public boolean shouldBeDisabledForProject(@NotNull Project project);
}
