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

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolder;
import com.intellij.openapi.vcs.FilePath;
import java.util.Collection;

/** #api192: addFiles takes {@link FilePath} objects in 2019.3 */
public class VcsRepositoryIgnoredFilesHolderCompat {
  private VcsRepositoryIgnoredFilesHolderCompat() {}

  public static void addFiles(
      VcsRepositoryIgnoredFilesHolder holder, Collection<? extends FilePath> paths) {
    holder.addFiles(paths);
  }
}
