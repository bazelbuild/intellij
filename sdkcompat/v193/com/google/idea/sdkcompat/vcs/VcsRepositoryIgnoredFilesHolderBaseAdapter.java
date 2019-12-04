package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;

/** #api191: VcsRepositoryIgnoredFilesHolderBase compat for 2019.1 */
public abstract class VcsRepositoryIgnoredFilesHolderBaseAdapter<RepositoryT extends Repository>
    extends VcsRepositoryIgnoredFilesHolderBase<RepositoryT> {
  public VcsRepositoryIgnoredFilesHolderBaseAdapter(
      RepositoryT repository,
      AbstractRepositoryManager<RepositoryT> repositoryManager,
      String updateQueueName,
      String rescanIdentityName) {
    super(repository, repositoryManager);
  }
}
