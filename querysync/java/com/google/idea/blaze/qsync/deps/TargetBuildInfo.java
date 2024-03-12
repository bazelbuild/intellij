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
package com.google.idea.blaze.qsync.deps;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import java.util.Optional;

@AutoValue
abstract class TargetBuildInfo {
  abstract Optional<JavaArtifactInfo> javaInfo();

  // TODO(b/323346056) Add cc info here.

  abstract DependencyBuildContext buildContext();

  static TargetBuildInfo forJavaTarget(
      JavaArtifactInfo javaInfo, DependencyBuildContext buildContext) {
    return new AutoValue_TargetBuildInfo(Optional.of(javaInfo), buildContext);
  }
}
