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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.io.VfsUtils;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * A {@link SyntheticLibrary} pointing to a list of external files for a language. Only supports one
 * instance per value of presentableText.
 */
public final class BlazeExternalSyntheticLibrary extends SyntheticLibrary
    implements ItemPresentation {
  private static final Logger logger = Logger.getInstance(BlazeExternalSyntheticLibrary.class);

  private final String presentableText;
  private volatile ImmutableList<File> files;
  private volatile ImmutableSet<VirtualFile> validFiles = ImmutableSet.of();

  private final AtomicBoolean inProgress = new AtomicBoolean();
  private volatile Instant lastUpdateTime;
  private volatile Future<?> validFilesUpdate = CompletableFuture.completedFuture(null);

  /**
   * @param presentableText user-facing text used to name the library. It's also used to implement
   *     equals, hashcode -- there must only be one instance per value of this text
   * @param files the list of files comprising this library
   */
  public BlazeExternalSyntheticLibrary(
      Project project,
      String presentableText,
      ImmutableList<File> files,
      @Nullable ListenableFuture<Collection<File>> futureFiles) {
    this.presentableText = presentableText;
    this.files = files;
    if (futureFiles != null) {
      futureFiles.addListener(
          () -> addFutureFiles(project, futureFiles), MoreExecutors.directExecutor());
    }
    updateValidFiles();
    ExternalLibraryUpdater.getInstance(project).addExternalLibrary(this);
  }

  public BlazeExternalSyntheticLibrary(
      Project project, String presentableText, ImmutableList<File> files) {
    this(project, presentableText, files, null);
  }

  private void addFutureFiles(Project project, ListenableFuture<Collection<File>> futureFiles) {
    Collection<File> computedFiles = ImmutableList.of();
    try {
      computedFiles = futureFiles.get();
    } catch (InterruptedException ignored) {
      // ignored
    } catch (ExecutionException e) {
      logger.warn(e);
    }
    if (computedFiles.isEmpty()) {
      return;
    }
    ImmutableList<File> oldFiles = files;
    this.files =
        Streams.concat(oldFiles.stream(), computedFiles.stream()).collect(toImmutableList());
    if (updateValidFiles()) {
      ExternalLibraryUpdater.getInstance(project).reindexRoots(project);
    }
  }

  /** Returns true if files were updated. */
  boolean updateValidFiles() {
    ImmutableSet<VirtualFile> oldFiles = this.validFiles;
    this.validFiles =
        this.files.stream()
            .map(VfsUtils::resolveVirtualFile)
            .filter(Objects::nonNull)
            .filter(VirtualFile::isValid)
            .collect(toImmutableSet());
    return !validFiles.equals(oldFiles);
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return presentableText;
  }

  @Override
  public ImmutableSet<VirtualFile> getSourceRoots() {
    // Rebuild valid files set in the background if we haven't updated in the last hour.
    if (!ExternalLibraryUpdater.updateExternalSyntheticLibraryOnFrameActivation.getValue()
        && validFilesUpdate.isDone()
        && Duration.between(lastUpdateTime, Instant.now()).toHours() >= 1
        && inProgress.compareAndSet(false, true)) {
      this.validFilesUpdate =
          ApplicationManager.getApplication()
              .executeOnPooledThread(
                  () -> {
                    updateValidFiles();
                    this.lastUpdateTime = Instant.now();
                  });
      inProgress.set(false);
    }
    // this must return a set, otherwise SyntheticLibrary#contains will create a new set each time
    // it's invoked (very frequently, on the EDT)
    return validFiles;
  }

  @Override
  public boolean equals(Object o) {
    // intended to be only a single instance added to the project for each value of presentableText
    return o instanceof BlazeExternalSyntheticLibrary
        && presentableText.equals(((BlazeExternalSyntheticLibrary) o).presentableText);
  }

  @Override
  public int hashCode() {
    // intended to be only a single instance added to the project for each value of presentableText
    return presentableText.hashCode();
  }

  @Nullable
  @Override
  public String getLocationString() {
    return null;
  }

  @Override
  public Icon getIcon(boolean unused) {
    return BlazeIcons.Blaze;
  }
}
