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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.FileBasedIndex;
import icons.BlazeIcons;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * A {@link SyntheticLibrary} pointing to a list of external files for a language. Only supports one
 * instance per value of presentableText.
 */
public final class BlazeExternalSyntheticLibrary extends SyntheticLibrary
    implements ItemPresentation {
  private final String presentableText;
  private final Set<File> files;
  private final Set<VirtualFile> validFiles;

  private static final BoolExperiment reindexAfterAddingExternalLibraryFiles =
      new BoolExperiment("reindex.after.adding.external.library.files", true);
  private static final ScheduledExecutorService indexingScheduler =
      Executors.newSingleThreadScheduledExecutor();
  private static final AtomicReference<ScheduledFuture<?>> indexingFuture = new AtomicReference<>();

  /**
   * Constructs library with an initial set of valid {@link VirtualFile}s.
   *
   * @param presentableText user-facing text used to name the library. It's also used to implement
   *     equals, hashcode -- there must only be one instance per value of this text
   * @param files collection of files that this synthetic library is responsible for.
   */
  BlazeExternalSyntheticLibrary(String presentableText, Collection<File> files) {
    this.presentableText = presentableText;
    this.files = new HashSet<>();
    this.validFiles = Sets.newConcurrentHashSet();
    addFiles(files, false);
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return presentableText;
  }

  void removeInvalidFiles(Collection<VirtualFile> deletedFiles) {
    if (deletedFiles.stream().anyMatch(VirtualFile::isDirectory)) {
      validFiles.removeIf(f -> !f.isValid());
    } else {
      validFiles.removeAll(deletedFiles);
    }
  }

  void restoreMissingFiles() {
    if (validFiles.size() < files.size()) {
      Sets.difference(
              files,
              validFiles.stream()
                  .filter(VirtualFile::isValid)
                  .map(VfsUtil::virtualToIoFile)
                  .collect(toImmutableSet()))
          .stream()
          .map(VfsUtils::resolveVirtualFile)
          .filter(Objects::nonNull)
          .forEach(validFiles::add);
    }
  }

  public void addFiles(Collection<File> files, boolean reindex) {
    if (this.files.addAll(files)) {
      files.stream()
          .map(VfsUtils::resolveVirtualFile)
          .filter(Objects::nonNull)
          .forEach(this.validFiles::add);
      if (reindex && reindexAfterAddingExternalLibraryFiles.getValue()) {
        requestReindex();
      }
    }
  }

  private static void requestReindex() {
    ScheduledFuture<?> previous =
        indexingFuture.getAndSet(
            indexingScheduler.schedule(
                () -> FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID),
                5,
                TimeUnit.SECONDS));
    if (previous != null && !previous.isDone() && !previous.isCancelled()) {
      previous.cancel(false);
    }
  }

  @Override
  public Set<VirtualFile> getSourceRoots() {
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
    return BlazeIcons.Logo;
  }
}
