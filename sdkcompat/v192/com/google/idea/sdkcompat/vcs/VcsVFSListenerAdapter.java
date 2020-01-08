package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsVFSListener;
import java.util.List;

/** #api192: VcsVFSListener compatibility bridge */
public abstract class VcsVFSListenerAdapter extends VcsVFSListener {

  protected VcsVFSListenerAdapter(AbstractVcs<?> vcs) {
    super(vcs);
    installListeners();
  }

  /** #api192: remove */
  protected VcsVFSListenerAdapter(Project project, AbstractVcs<?> vcs) {
    super(vcs);
    installListeners();
  }

  /** #api192: move to caller */
  protected AllDeletedFiles acquireAllDeletedFiles() {
    return new AllDeletedFiles(myDeletedFiles, myDeletedWithoutConfirmFiles);
  }

  /** Similar to VcsVFSListener.AllDeletedFiles in 2019.3 */
  protected static class AllDeletedFiles {
    public final List<FilePath> deletedFiles;
    public final List<FilePath> deletedWithoutConfirmFiles;

    AllDeletedFiles(List<FilePath> deletedFiles, List<FilePath> deletedWithoutConfirmFiles) {
      this.deletedFiles = deletedFiles;
      this.deletedWithoutConfirmFiles = deletedWithoutConfirmFiles;
    }
  }
}
