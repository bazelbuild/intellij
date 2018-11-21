/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeSession;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;

/** Adapter for {@link MergeSessionEx}, which doesn't exist in #api181. */
public abstract class MergeSessionAdapter implements MergeSession {

  @Override
  public void conflictResolvedForFile(VirtualFile file, Resolution resolution) {
    conflictResolvedForFiles(ImmutableList.of(file), resolution);
  }

  /** @deprecated This isn't called in #api181, but is called in later versions. */
  @Deprecated
  public abstract void acceptFilesRevisions(List<VirtualFile> files, Resolution resolution)
      throws VcsException;

  public abstract void conflictResolvedForFiles(List<VirtualFile> files, Resolution resolution);
}
