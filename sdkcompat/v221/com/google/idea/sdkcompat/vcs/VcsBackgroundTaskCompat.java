package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.VcsBackgroundTask;
import java.util.Collection;

/**
 * Compat for {@link VcsBackgroundTask}. VcsBackgroundTask changed the number of parameters in
 * constructor starting with 2021.3.
 *
 * <p>To cleanup delete the class and use VcsBackgroundTask directly.
 *
 * <p>#api212
 */
public abstract class VcsBackgroundTaskCompat<T> extends VcsBackgroundTask<T> {

  public VcsBackgroundTaskCompat(
      Project project,
      String title,
      PerformInBackgroundOption backgroundOption,
      Collection<? extends T> itemsToProcess) {
    super(project, title, itemsToProcess);
  }
}
