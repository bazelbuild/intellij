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
package com.google.idea.blaze.cpp;

import com.google.auto.value.AutoValue;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents the Xcode settings a C compiler needs to run.
 */
@AutoValue
public abstract class XcodeCompilerSettings {
  static XcodeCompilerSettings create(Path developerDir, Path sdkRoot) {
    return new AutoValue_XcodeCompilerSettings(developerDir, sdkRoot);
  }

  abstract Path getDeveloperDir();
  abstract Path getSdkRoot();
}
