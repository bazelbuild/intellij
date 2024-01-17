package com.google.idea.sdkcompat.fig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.List;

/** Compat class for HgVFSListener */
public abstract class AbstractHgVFSListener extends VcsVFSListener {
  protected AbstractHgVFSListener(final Project project, final AbstractVcs vcs) {
    super(project, vcs);
  }

  @Override
  protected boolean isDirectoryVersioningSupported() {
    return false;
  }

  @Override
  protected VcsDeleteType needConfirmDeletion(final VirtualFile file) {
    return ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles().contains(file)
        ? VcsDeleteType.IGNORE
        : VcsDeleteType.CONFIRM;
  }

  protected abstract void skipNotUnderHg(Collection<FilePath> filesToFilter);

  protected abstract List<FilePath> processAndGetVcsIgnored(List<FilePath> filePaths);

  protected void acquireFilesToDelete(
      List<FilePath> filesToDelete, List<FilePath> filesToConfirmDeletion) {

    AllDeletedFiles allDeletedFiles = myProcessor.acquireAllDeletedFiles();
    filesToDelete.addAll(allDeletedFiles.deletedWithoutConfirmFiles);
    filesToConfirmDeletion.addAll(allDeletedFiles.deletedFiles);

    // skip files which are not under Mercurial
    skipNotUnderHg(filesToDelete);
    skipNotUnderHg(filesToConfirmDeletion);

    filesToDelete.removeAll(processAndGetVcsIgnored(filesToDelete));
    filesToConfirmDeletion.removeAll(processAndGetVcsIgnored(filesToConfirmDeletion));
  }
}
