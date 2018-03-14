/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.Optional;

interface JavaFastBuildConfigurationRunnerFactory {

  ExtensionPointName<JavaFastBuildConfigurationRunnerFactory> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FastBuildConfigurationRunnerFactory");

  static Optional<JavaFastBuildConfigurationRunnerFactory> getInstance(BuildSystem buildSystem) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(f -> f.appliesTo(buildSystem)).findAny();
  }

  boolean appliesTo(BuildSystem buildSystem);

  BlazeCommandRunConfigurationRunner createRunner();
}
