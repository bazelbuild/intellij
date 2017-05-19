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
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId;
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.IdCase;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Build event protocol implementation to get build results.
 *
 * <p>The build even protocol (BEP for short) is a proto-based protocol used by bazel to communicate
 * build events.
 */
class BuildResultHelperBep implements BuildResultHelper {
  private static final Logger logger = Logger.getInstance(BuildResultHelperBep.class);
  private final File outputFile;
  private final Predicate<String> fileFilter;
  private ImmutableList<File> result;

  BuildResultHelperBep(Predicate<String> fileFilter) {
    this.fileFilter = fileFilter;
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    String suffix = UUID.randomUUID().toString();
    String fileName = "intellij-bep-" + suffix;
    this.outputFile = new File(tempDir, fileName);
  }

  @Override
  public List<String> getBuildFlags() {
    return ImmutableList.of("--experimental_build_event_binary_file=" + outputFile.getPath());
  }

  @Override
  public OutputStream stderr(LineProcessor... lineProcessors) {
    return LineProcessingOutputStream.of(ImmutableList.copyOf(lineProcessors));
  }

  @Override
  public ImmutableList<File> getBuildArtifacts() {
    if (result == null) {
      result = readResult();
    }
    return result;
  }

  private ImmutableList<File> readResult() {
    ImmutableList.Builder<File> result = ImmutableList.builder();
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(outputFile))) {
      BuildEvent buildEvent;
      while ((buildEvent = BuildEvent.parseDelimitedFrom(inputStream)) != null) {
        BuildEventId buildEventId = buildEvent.getId();
        // Note: This doesn't actually work. BEP does not issue these for actions
        // that don't execute during the build, so we can't find the files
        // for a no-op build the way we can for --experimental_show_artifacts
        if (buildEventId.getIdCase() == IdCase.ACTION_COMPLETED) {
          String output = buildEventId.getActionCompleted().getPrimaryOutput();
          if (fileFilter.test(output)) {
            result.add(new File(output));
          }
        }
      }
    } catch (IOException e) {
      logger.error(e);
      return ImmutableList.of();
    }
    if (!outputFile.delete()) {
      logger.warn("Could not delete BEP output file: " + outputFile);
    }
    return result.build();
  }
}
