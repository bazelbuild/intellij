package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.ignore.VcsIgnoredHolderUpdateListener;
import com.intellij.openapi.vcs.FilePath;
import java.util.Collection;

/** #api192 Compatibility bridge for VcsIgnoredHolderUpdateListener */
public abstract class VcsIgnoredHolderUpdateListenerAdapter
    implements VcsIgnoredHolderUpdateListener {

  @Override
  public void updateFinished(Collection<FilePath> ignoredPaths) {
    updateFinished(ignoredPaths, true);
  }

  public abstract void updateFinished(Collection<FilePath> ignoredPaths, boolean isFullRescan);
}
