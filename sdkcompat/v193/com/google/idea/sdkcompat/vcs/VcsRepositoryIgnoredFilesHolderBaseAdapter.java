/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** VcsRepositoryIgnoredFilesHolderBase compat (#api192: last changed in 2019.3) */
public abstract class VcsRepositoryIgnoredFilesHolderBaseAdapter<RepositoryT extends Repository>
    extends VcsRepositoryIgnoredFilesHolderBase<RepositoryT> {
  public VcsRepositoryIgnoredFilesHolderBaseAdapter(
      RepositoryT repository,
      AbstractRepositoryManager<RepositoryT> repositoryManager,
      String updateQueueName,
      String rescanIdentityName) {
    super(repository, repositoryManager);
  }

  /** #api192: Set&lt;VirtualFile> refactored to Set&lt;FilePath> in 2019.3 */
  protected abstract Set<VirtualFile> requestIgnoredVF(
      @Nullable Collection<? extends FilePath> paths);

  @Override
  protected Set<FilePath> requestIgnored(@Nullable Collection<? extends FilePath> collection)
      throws VcsException {
    Set<VirtualFile> files = requestIgnoredVF(collection);
    return files.stream().map(VcsUtil::getFilePath).collect(Collectors.toSet());
  }
}
