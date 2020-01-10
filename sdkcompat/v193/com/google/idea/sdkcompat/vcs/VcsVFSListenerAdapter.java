package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsVFSListener;

/** #api192: VcsVFSListener compatibility bridge */
public abstract class VcsVFSListenerAdapter extends VcsVFSListener {

  protected VcsVFSListenerAdapter(AbstractVcs vcs) {
    super(vcs);
    installListeners();
  }

  /** #api192: remove */
  protected VcsVFSListenerAdapter(Project project, AbstractVcs vcs) {
    super(vcs);
    installListeners();
  }

  /** #api192: move to caller */
  public AllDeletedFiles acquireAllDeletedFiles() {
    return this.myProcessor.acquireAllDeletedFiles();
  }
}
