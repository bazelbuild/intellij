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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;

final class BazelTestEnvironmentVariables implements FastBuildTestEnvironmentModifier {

  @Override
  public void modify(
      ModifiableJavaCommand commandBuilder,
      Kind kind,
      FastBuildInfo fastBuildInfo,
      BlazeInfo blazeInfo) {

    String runfilesDir = commandBuilder.getEnvironmentVariable("TEST_SRCDIR");

    // These come from StandaloneTestStrategy
    commandBuilder
        .addEnvironmentVariable("JAVA_RUNFILES", runfilesDir)
        .addEnvironmentVariable("PATH", System.getenv("PATH"))
        .addEnvironmentVariable("PYTHON_RUNFILES", runfilesDir)
        .addEnvironmentVariable("RUN_UNDER_RUNFILES", "1")
        .addEnvironmentVariable("RUNFILES_DIR", runfilesDir)
        .addEnvironmentVariable("TZ", "UTC");

    // This comes from tools/test/test-setup.sh
    commandBuilder.addEnvironmentVariable(
        "GTEST_TMP_DIR", commandBuilder.getEnvironmentVariable("TEST_TMPDIR"));
  }

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Bazel);
  }
}
