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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
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
  private final String presentableText;
  private final ImmutableList<VirtualFile> files;
  private final AtomicBoolean inProgress = new AtomicBoolean();
  private volatile ImmutableSet<VirtualFile> validFiles;
  private volatile Instant lastUpdateTime;
  private volatile Future<?> validFilesUpdate;

  /**
   * @param presentableText user-facing text used to name the library. It's also used to implement
   *     equals, hashcode -- there must only be one instance per value of this text
   * @param files the list of files comprising this library
   */
  public BlazeExternalSyntheticLibrary(String presentableText, ImmutableList<VirtualFile> files) {
    this.presentableText = presentableText;
    this.files = files;
    updateValidFiles();
    this.validFilesUpdate = CompletableFuture.completedFuture(null);
  }

  private void updateValidFiles() {
    this.validFiles = this.files.stream().filter(VirtualFile::isValid).collect(toImmutableSet());
    this.lastUpdateTime = Instant.now();
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return presentableText;
  }

  @Override
  public ImmutableSet<VirtualFile> getSourceRoots() {
    // Rebuild valid files set in the background if we haven't updated in the last hour.
    if (validFilesUpdate.isDone()
        && Duration.between(lastUpdateTime, Instant.now()).toHours() >= 1
        && inProgress.compareAndSet(false, true)) {
      this.validFilesUpdate =
          ApplicationManager.getApplication().executeOnPooledThread(this::updateValidFiles);
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
