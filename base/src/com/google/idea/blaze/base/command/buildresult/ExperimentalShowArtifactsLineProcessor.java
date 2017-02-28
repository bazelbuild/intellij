/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import java.io.File;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/** Collects the output of --experimental_show_artifacts */
class ExperimentalShowArtifactsLineProcessor implements LineProcessingOutputStream.LineProcessor {
  private static final String OUTPUT_START = "Build artifacts:";
  private static final String OUTPUT_MARKER = ">>>";

  private final ImmutableList.Builder<File> fileList;
  private final Predicate<String> filter;
  private boolean afterBuildResult = false;

  ExperimentalShowArtifactsLineProcessor(
      ImmutableList.Builder<File> fileList, Predicate<String> filter) {
    this.fileList = fileList;
    this.filter = filter;
  }

  @Override
  public boolean processLine(@NotNull String line) {
    if (!afterBuildResult) {
      afterBuildResult = line.equals(OUTPUT_START);
      return !afterBuildResult;
    }
    if (!line.startsWith(OUTPUT_MARKER)) {
      return true;
    }
    String fileName = line.substring(OUTPUT_MARKER.length());
    if (filter.test(fileName)) {
      fileList.add(new File(fileName));
    }
    return false;
  }
}
