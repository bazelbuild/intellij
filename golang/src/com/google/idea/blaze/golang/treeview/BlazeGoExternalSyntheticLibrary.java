/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.golang.treeview;

import com.google.common.collect.ImmutableSet;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.Icon;

/** Represents a {@link SyntheticLibrary} with a mutable set of child files. */
final class BlazeGoExternalSyntheticLibrary extends SyntheticLibrary implements ItemPresentation {

  private final SortedSet<VirtualFile> childFiles;
  private final String presentableText;

  BlazeGoExternalSyntheticLibrary(String presentableText, ImmutableSet<VirtualFile> childFiles) {
    this.childFiles = new TreeSet<>(Comparator.comparing(VirtualFile::toString));
    this.childFiles.addAll(childFiles);
    this.presentableText = presentableText;
  }

  void addFiles(ImmutableSet<VirtualFile> files) {
    childFiles.addAll(files);
  }

  @Override
  public String getPresentableText() {
    return presentableText;
  }

  @Override
  public Icon getIcon(boolean unused) {
    return BlazeIcons.Logo;
  }

  @Override
  public SortedSet<VirtualFile> getSourceRoots() {
    return childFiles;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof BlazeGoExternalSyntheticLibrary
        && ((BlazeGoExternalSyntheticLibrary) o).presentableText.equals(presentableText)
        && ((BlazeGoExternalSyntheticLibrary) o).getSourceRoots().equals(getSourceRoots());
  }

  @Override
  public int hashCode() {
    return presentableText.hashCode();
  }
}
