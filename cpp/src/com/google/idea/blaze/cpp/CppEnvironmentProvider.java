/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.cpp;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;

import javax.annotation.Nullable;

/**
 * Creates and configures the environment for a specific compiler. At the moment, only the MSVC compiler requires a
 * custom configuration.
 */
public interface CppEnvironmentProvider {

  ExtensionPointName<CppEnvironmentProvider> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.cpp.CppEnvironmentProvider");

  static CidrToolEnvironment createEnvironment(BlazeCompilerSettings settings) {
    for (final var provider : EP_NAME.getExtensions()) {
      final var environment = provider.create(settings);

      if (environment != null) {
        return environment;
      }
    }

    return new CidrToolEnvironment();
  }

  @Nullable CidrToolEnvironment create(BlazeCompilerSettings settings);
}
