package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.vcs.AbstractVcs;

/** #api192: Constructor parameters change */
public abstract class AbstractRepositoryManagerAdapter<T extends Repository>
    extends AbstractRepositoryManager<T> {

  protected AbstractRepositoryManagerAdapter(AbstractVcs<?> vcs, String repoDirName) {
    super(VcsRepositoryManager.getInstance(vcs.getProject()), vcs, repoDirName);
  }
}
