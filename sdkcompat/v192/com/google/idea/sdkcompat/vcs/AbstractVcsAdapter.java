package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

/** #api192: generic parameters dropped in 2019.3 */
public abstract class AbstractVcsAdapter<ComListT extends CommittedChangeList>
    extends AbstractVcs<ComListT> {

  public AbstractVcsAdapter(@NotNull Project project, String name) {
    super(project, name);
  }
}
