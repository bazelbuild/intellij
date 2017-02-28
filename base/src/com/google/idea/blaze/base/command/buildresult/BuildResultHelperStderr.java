/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream.LineProcessor;
import com.google.idea.blaze.base.command.BlazeFlags;
import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Predicate;

class BuildResultHelperStderr implements BuildResultHelper {
  private final ImmutableList.Builder<File> buildArtifacts = ImmutableList.builder();
  private final ExperimentalShowArtifactsLineProcessor experimentalShowArtifactsLineProcessor;
  private ImmutableList<File> result;

  BuildResultHelperStderr(Predicate<String> fileFilter) {
    experimentalShowArtifactsLineProcessor =
        new ExperimentalShowArtifactsLineProcessor(buildArtifacts, fileFilter);
  }

  @Override
  public List<String> getBuildFlags() {
    return ImmutableList.of(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS);
  }

  @Override
  public OutputStream stderr(LineProcessor... lineProcessors) {
    return LineProcessingOutputStream.of(
        ImmutableList.<LineProcessor>builder()
            .add(experimentalShowArtifactsLineProcessor)
            .add(lineProcessors)
            .build());
  }

  @Override
  public ImmutableList<File> getBuildArtifacts() {
    if (result == null) {
      result = buildArtifacts.build();
    }
    return result;
  }
}
