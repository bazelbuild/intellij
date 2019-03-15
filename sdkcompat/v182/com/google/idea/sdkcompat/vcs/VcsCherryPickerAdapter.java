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

import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import java.util.Collection;
import java.util.List;

/** #api183: added generic wildcards in 2019.1 */
public abstract class VcsCherryPickerAdapter extends VcsCherryPicker {

  @Override
  public void cherryPick(List<VcsFullCommitDetails> commits) {
    cherryPickImpl(commits);
  }

  protected abstract void cherryPickImpl(List<? extends VcsFullCommitDetails> commits);

  @Override
  public boolean canHandleForRoots(Collection<VirtualFile> roots) {
    return canHandleForRootsImpl(roots);
  }

  protected abstract boolean canHandleForRootsImpl(Collection<? extends VirtualFile> roots);
}
