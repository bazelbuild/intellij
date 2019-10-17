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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeSessionEx;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;

/** #api191: generics added to method signatures in 2019.2 */
public interface MergeSessionExAdapter extends MergeSessionEx {

  void doAcceptFilesRevisions(List<? extends VirtualFile> files, Resolution resolution)
      throws VcsException;

  @Override
  default void acceptFilesRevisions(List<? extends VirtualFile> list, Resolution resolution)
      throws VcsException {
    doAcceptFilesRevisions(list, resolution);
  }

  void doConflictResolvedForFiles(List<? extends VirtualFile> list, Resolution resolution);

  @Override
  default void conflictResolvedForFiles(List<? extends VirtualFile> list, Resolution resolution) {
    doConflictResolvedForFiles(list, resolution);
  }
}
