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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import java.util.List;

/** Compat for {@link ChangeListManager}. Remove when #api183 is no longer supported. */
public class ChangeListManagerAdapter {

  // #api183: changed signature in 2019.1
  @VisibleForTesting
  public static void ensureUpToDate(Project project) {
    ChangeListManagerImpl.getInstanceImpl(project).ensureUpToDate();
  }

  /** Compat for test */
  @VisibleForTesting
  public abstract static class TestChangeListManagerAdapter extends ChangeListManager {
    @Override // #api183: wildcard added to List<> in 2019.1
    public void commitChanges(LocalChangeList changeList, List<? extends Change> changes) {
      throw new UnsupportedOperationException("TestChangeListManager#commitChanges()");
    }

    @Override // #api183: wildcard added to List<> in 2019.1
    public void reopenFiles(List<? extends FilePath> paths) {
      throw new UnsupportedOperationException("TestChangeListManager#reopenFiles()");
    }
  }
}
