/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb.base;

import com.google.idea.testing.headless.BazelInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ObjectUtils;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class AllowedVfsRoot {

  private final Path root;
  private final boolean recursive;

  private AllowedVfsRoot(Path root, boolean recursive) {
    assert !root.isAbsolute() : "the path should be relative to the execution root";

    this.root = root;
    this.recursive = recursive;
  }

  public static AllowedVfsRoot flat(String path) {
    return new AllowedVfsRoot(Path.of(path).normalize(), false);
  }

  public static AllowedVfsRoot recursive(String path) {
    return new AllowedVfsRoot(Path.of(path).normalize(), true);
  }

  public static AllowedVfsRoot bazelBinFlat(BazelInfo info, String path) {
    return new AllowedVfsRoot(info.executionRoot().relativize(info.bazelBin().resolve(path)).normalize(), false);
  }

  public static AllowedVfsRoot bazelBinRecursive(BazelInfo info, String path) {
    return new AllowedVfsRoot(info.executionRoot().relativize(info.bazelBin().resolve(path)).normalize(), true);
  }

  public boolean contains(Path path) {
    assert !path.isAbsolute() : "the path should be relative to the execution root";

    if (recursive) {
      return FileUtil.isAncestor(root.toFile(), path.toFile(), false);
    } else {
      return FileUtil.pathsEqual(root.toString(), ObjectUtils.coalesce(path.getParent(), Path.of("")).toString());
    }
  }

  @Override
  public @NotNull String toString() {
    if (recursive) {
      return root.toString();
    } else {
      return "|" + root.toString();
    }
  }
}

