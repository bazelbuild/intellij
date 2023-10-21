package com.google.idea.sdkcompat.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.VcsBaseContentProvider;
import com.intellij.openapi.vcs.impl.VcsFileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;

/** Compat class that provide VcsBaseContentProvider delegator. */
public class VcsBaseContentProviderDelegator {
  private final VcsBaseContentProvider delegate;

  public VcsBaseContentProviderDelegator(Project project) {
    this.delegate = project.getComponent(VcsFileStatusProvider.class);
  }

  public boolean isSupported(VirtualFile file) {
    return delegate.isSupported(file);
  }

  public VcsBaseContentProvider.BaseContent getBaseRevision(VirtualFile file) {
    return delegate.getBaseRevision(file);
  }
}
