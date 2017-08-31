/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.concurrent.Immutable;

/** Summary of differences between two {@link BlazeConfigurationResolver}s. */
@Immutable
final class BlazeConfigurationResolverDiff {

  private final ImmutableSet<VirtualFile> changedFiles;
  private final boolean hasRemovedTargets;

  BlazeConfigurationResolverDiff(
      ImmutableSet<VirtualFile> changedFiles, boolean hasRemovedTargets) {
    this.changedFiles = changedFiles;
    this.hasRemovedTargets = hasRemovedTargets;
  }

  ImmutableSet<VirtualFile> getChangedFiles() {
    return changedFiles;
  }

  boolean hasChanges() {
    return hasRemovedTargets || !changedFiles.isEmpty();
  }
}
