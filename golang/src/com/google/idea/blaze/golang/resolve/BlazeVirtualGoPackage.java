/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Represents a virtual go package. Can hold go files in addition to subdirectories. */
public class BlazeVirtualGoPackage extends BlazeVirtualGoDirectory {
  private final ImmutableList<File> goFiles;
  private NewVirtualFile[] cachedGoFiles = null;

  BlazeVirtualGoPackage(String name, BlazeVirtualGoDirectory parent, ImmutableList<File> children) {
    super(name, parent);
    this.goFiles = children;
  }

  @Nullable
  @Override
  public NewVirtualFile findChild(String name) {
    NewVirtualFile child = super.findChild(name);
    if (child != null) {
      return child;
    }
    return Arrays.stream(cachedGoFiles)
        .filter(f -> f.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  @Override
  public NewVirtualFile[] getChildren() {
    return ObjectArrays.concat(super.getChildren(), getGoFiles(), NewVirtualFile.class);
  }

  public NewVirtualFile[] getGoFiles() {
    if (cachedGoFiles == null) {
      LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
      cachedGoFiles =
          goFiles
              .stream()
              .map(lfs::findFileByIoFile)
              .filter(Objects::nonNull)
              .toArray(NewVirtualFile[]::new);
    }
    return cachedGoFiles;
  }
}
