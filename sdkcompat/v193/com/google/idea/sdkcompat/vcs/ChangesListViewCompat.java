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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Compat for {@link ChangesListView}. Remove when ChangesListViewCompat is no longer supported. */
public final class ChangesListViewCompat {
  private ChangesListViewCompat() {}

  /**
   * #api192: The DataKey was changed from UNVERSIONED_FILES_DATA_KEY to
   * UNVERSIONED_FILE_PATHS_DATA_KEY but the type also changed from Stream&lt;VirtualFile> to
   * Stream&lt;FilePath>
   */
  @Nullable
  public static Stream<VirtualFile> getUnversionedFileStreamFromEvent(AnActionEvent e) {
    Stream<FilePath> filePathStream = e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY);
    if (filePathStream == null) {
      return null;
    }
    return filePathStream.map(FilePath::getVirtualFile);
  }
}
