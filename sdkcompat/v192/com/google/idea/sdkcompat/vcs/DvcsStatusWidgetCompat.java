package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Compat adapter for {@link DvcsStatusWidget}.
 *
 * <p>It has a {@link #ID()} method in 2020.1, which won't pass CheckLint unless annotated
 * with @Override. #api193
 */
public abstract class DvcsStatusWidgetCompat<T extends Repository> extends DvcsStatusWidget<T> {
  protected DvcsStatusWidgetCompat(@NotNull Project project, @NotNull String prefix) {
    super(project, prefix);
  }

  public abstract String getId();
}
