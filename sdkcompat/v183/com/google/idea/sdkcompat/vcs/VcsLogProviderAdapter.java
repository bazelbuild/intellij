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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import java.util.Collection;
import java.util.List;

/** #api183: added wildcards bounds 2019.1 */
public abstract class VcsLogProviderAdapter implements VcsLogProvider {

  @Override
  public LogData readAllHashes(VirtualFile root, Consumer<TimedVcsCommit> commitConsumer) {
    return readAllHashesImpl(root, commitConsumer);
  }

  protected abstract LogData readAllHashesImpl(
      VirtualFile root, Consumer<? super TimedVcsCommit> commitConsumer);

  @Override
  public void readAllFullDetails(VirtualFile root, Consumer<VcsFullCommitDetails> commitConsumer)
      throws VcsException {
    readFullDetails(root, ContainerUtil.newArrayList(), commitConsumer);
  }

  @Override
  public void readFullDetails(
      VirtualFile root,
      List<String> hashes,
      Consumer<VcsFullCommitDetails> commitConsumer,
      boolean fast)
      throws VcsException {
    readFullDetailsImpl(root, hashes, commitConsumer, fast);
  }

  protected abstract void readFullDetailsImpl(
      VirtualFile root,
      List<String> hashes,
      Consumer<? super VcsFullCommitDetails> commitConsumer,
      boolean fast)
      throws VcsException;

  @Override
  public Disposable subscribeToRootRefreshEvents(
      Collection<VirtualFile> roots, VcsLogRefresher refresher) {
    return subscribeToRootRefreshEventsImpl(roots, refresher);
  }

  protected abstract Disposable subscribeToRootRefreshEventsImpl(
      Collection<? extends VirtualFile> roots, VcsLogRefresher refresher);
}
