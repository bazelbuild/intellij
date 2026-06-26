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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ObjectUtils;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class AllowedVfsRoot {

  public enum Config {ANY, FASTBUILD, DEBUG}

  private final Config config;
  private final Path path;
  private final boolean recursive;

  private AllowedVfsRoot(Config config, Path path, boolean recursive) {
    assert !path.isAbsolute() : "the path should be relative to the execution path";

    this.config = config;
    this.path = path;
    this.recursive = recursive;
  }

  public static AllowedVfsRoot flat(Config config, String path) {
    return new AllowedVfsRoot(config, Path.of(path).normalize(), false);
  }

  public static AllowedVfsRoot recursive(Config config, String path) {
    return new AllowedVfsRoot(config, Path.of(path).normalize(), true);
  }

  public boolean contains(Path path) {
    assert !path.isAbsolute() : "the path should be relative to the execution path";
    assert path.getNameCount() > 3 : "the path should contain at least more then 3 elemnts";
    assert path.getName(0).toString().equals("bazel-out") : "the path should start with bazel-out";
    assert path.getName(2).toString().equals("bin") : "the path should reside in bazel-bin";

    final var effectiveConfig = path.getName(1).toString();
    if (config == Config.FASTBUILD && !effectiveConfig.contains("fastbuild")) return false;
    if (config == Config.DEBUG && !effectiveConfig.contains("dbg")) return false;

    final var effectivePath = path.subpath(3, path.getNameCount());
    if (recursive) {
      return FileUtil.isAncestor(this.path.toFile(), effectivePath.toFile(), false);
    } else {
      return FileUtil.pathsEqual(this.path.toString(), ObjectUtils.coalesce(effectivePath.getParent().toString(), ""));
    }
  }

  @Override
  public @NotNull String toString() {
    final var builder = new StringBuilder();

    if (recursive) {
      builder.append("|");
    } else {
      builder.append("-");
    }

    builder.append("[");
    builder.append(config.toString());
    builder.append("]:");

    builder.append(path.toString());

    return builder.toString();
  }
}

