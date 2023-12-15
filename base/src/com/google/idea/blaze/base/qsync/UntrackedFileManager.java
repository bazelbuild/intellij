/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Set;

/** A class that keeps track of project files that are out of sync */
public class UntrackedFileManager {
  private final Set<Path> untrackedFiles = Sets.newConcurrentHashSet();
  private final Set<Path> modifiedBuildFiles = Sets.newConcurrentHashSet();

  @VisibleForTesting
  public UntrackedFileManager() {}

  public static UntrackedFileManager createWithListeners(
      Project project, Disposable parentDisposable) {
    UntrackedFileManager fileManager = new UntrackedFileManager();
    QuerySyncAsyncFileListener.createAndListen(project, fileManager, parentDisposable);
    ApplicationManager.getApplication()
        .getExtensionArea()
        .getExtensionPoint(SyncListener.EP_NAME)
        .registerExtension(
            new SyncListener() {
              @Override
              public void beforeQuerySync(Project project, BlazeContext context) {
                fileManager.clear();
              }
            },
            parentDisposable);
    return fileManager;
  }

  public void clear() {
    untrackedFiles.clear();
    modifiedBuildFiles.clear();
  }

  public void addUntrackedFile(Path path) {
    untrackedFiles.add(path);
  }

  public void addModifiedBuildFile(Path path) {
    modifiedBuildFiles.add(path);
  }

  public boolean hasUntrackedFile(Path path) {
    return untrackedFiles.contains(path);
  }

  public boolean hasModifiedBuildFile(Path path) {
    return modifiedBuildFiles.contains(path);
  }

  @VisibleForTesting
  public ImmutableSet<Path> getUntrackedFiles() {
    return ImmutableSet.copyOf(untrackedFiles);
  }

  @VisibleForTesting
  public ImmutableSet<Path> getModifiedBuildFiles() {
    return ImmutableSet.copyOf(modifiedBuildFiles);
  }
}
