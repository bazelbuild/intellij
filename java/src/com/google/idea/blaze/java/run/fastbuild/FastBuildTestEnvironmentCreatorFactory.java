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

import static com.google.common.base.Preconditions.checkState;

import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Optional;

interface FastBuildTestEnvironmentCreatorFactory {

  ExtensionPointName<FastBuildTestEnvironmentCreatorFactory> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FastBuildTestEnvironmentCreatorFactory");

  static FastBuildTestEnvironmentCreatorFactory getInstance(BuildSystem buildSystem) {
    Optional<FastBuildTestEnvironmentCreatorFactory> factory =
        Arrays.stream(EP_NAME.getExtensions()).filter(f -> f.appliesTo(buildSystem)).findAny();
    checkState(
        factory.isPresent(),
        "No FastBuildTestEnvironmentCreatorFactory for build system %s",
        buildSystem);
    return factory.get();
  }

  boolean appliesTo(BuildSystem buildSystem);

  FastBuildTestEnvironmentCreator getTestEnvironmentCreator(Project project);
}
