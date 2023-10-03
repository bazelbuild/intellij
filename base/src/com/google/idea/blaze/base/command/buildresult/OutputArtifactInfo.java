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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.base.Joiner;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import java.nio.file.Path;

/** A descriptor of an output artifact that contains data needed to identify the artifact. */
public interface OutputArtifactInfo {
  /** The blaze-out-relative path. */
  default String getRelativePath() {
    Path full = getPath();
    if (full.getName(0).toString().endsWith("-out")) {
      return full.subpath(1, full.getNameCount()).toString();
    } else {
      return full.toString();
    }
  }

  /** The path to this artifact as extracted from the build event stream. */
  Path getPath();

  static Path pathFromBesFile(BuildEventStreamProtos.File file) {
    return Path.of(Joiner.on('/').join(file.getPathPrefixList())).resolve(file.getName());
  }
}
