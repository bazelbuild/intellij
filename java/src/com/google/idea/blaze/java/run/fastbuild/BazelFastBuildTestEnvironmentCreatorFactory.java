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
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;

final class BazelFastBuildTestEnvironmentCreatorFactory
    implements FastBuildTestEnvironmentCreatorFactory {

  @Override
  public ImmutableSet<BuildSystem> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystem.Bazel);
  }

  @Override
  public FastBuildTestEnvironmentCreator getTestEnvironmentCreator(Project project) {
    return new FastBuildTestEnvironmentCreator(
        project,
        /* testClassProperty */ "bazel.test_suite",
        /* testRunner */ "com.google.testing.junit.runner.BazelTestRunner",
        /* roboelectricDepsPropertiesFinder */
        fastBuildInfo -> {
          throw new ExecutionException("Fast builds do not support android_local_test targets");
        });
  }
}
