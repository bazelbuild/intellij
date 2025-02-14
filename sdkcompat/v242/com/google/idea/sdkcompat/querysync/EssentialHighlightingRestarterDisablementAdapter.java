package com.google.idea.sdkcompat.querysync;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

// #api242
public interface EssentialHighlightingRestarterDisablementAdapter {

  public boolean shouldBeDisabledForProject(@NotNull Project project);
}
