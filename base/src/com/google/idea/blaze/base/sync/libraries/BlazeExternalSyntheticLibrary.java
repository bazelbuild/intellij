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

import com.google.common.collect.ImmutableSet;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * A {@link SyntheticLibrary} pointing to a list of external files for a language. Only supports one
 * instance per value of presentableText.
 */
public final class BlazeExternalSyntheticLibrary extends SyntheticLibrary
    implements ItemPresentation {
  private final String presentableText;
  private volatile ImmutableSet<VirtualFile> files;

  /**
   * @param presentableText user-facing text used to name the library. It's also used to implement
   *     equals, hashcode -- there must only be one instance per value of this text
   */
  BlazeExternalSyntheticLibrary(String presentableText) {
    this.presentableText = presentableText;
    this.files = ImmutableSet.of();
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return presentableText;
  }

  void updateFiles(ImmutableSet<VirtualFile> files) {
    this.files = files;
  }

  @Override
  public ImmutableSet<VirtualFile> getSourceRoots() {
    // this must return a set, otherwise SyntheticLibrary#contains will create a new set each time
    // it's invoked (very frequently, on the EDT)
    return files;
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
