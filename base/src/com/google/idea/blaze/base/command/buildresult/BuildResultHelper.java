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
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream.LineProcessor;
import com.google.idea.common.experiments.BoolExperiment;
import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Predicate;

/** Assists in getting build artifacts from a build operation. */
public interface BuildResultHelper {
  // This experiment does *not* work yet and should remain off
  BoolExperiment USE_BEP = new BoolExperiment("use.bep", false);

  /**
   * Constructs a new build result helper.
   *
   * @param files A filter for the output artifacts you are interested in.
   */
  static BuildResultHelper forFiles(Predicate<String> files) {
    return USE_BEP.getValue()
        ? new BuildResultHelperBep(files)
        : new BuildResultHelperStderr(files);
  }

  /**
   * Returns the build flags necessary for the build result helper to work.
   *
   * <p>The user must add these flags to their build command.
   */
  List<String> getBuildFlags();

  /**
   * Returns an output stream to be passed to the external task's stderr.
   *
   * <p>The user must pipe blaze's stderr to this output stream.
   *
   * @param lineProcessors Any additional line processors you want on stderr output.
   */
  OutputStream stderr(LineProcessor... lineProcessors);

  /**
   * Returns the build result. May only be called once the build is complete, or no artifacts will
   * be returned.
   *
   * @return The build artifacts from the build operation.
   */
  ImmutableList<File> getBuildArtifacts();
}
