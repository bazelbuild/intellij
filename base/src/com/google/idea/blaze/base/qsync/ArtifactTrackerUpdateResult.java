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
package com.google.idea.blaze.base.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;

/**
 * A result of updating artifact cache.
 *
 * <p>Instances of this class describe the difference between the state of the artifact cache before
 * and after the operation. They do not represent the result of build action that produced these
 * artifacts.
 *
 * <p><b>DO NOT USE</b> for purposes other than logging or displaying the result in the UI.
 */
@AutoValue
public abstract class ArtifactTrackerUpdateResult {
  public abstract ImmutableSet<Path> updatedFiles();

  public abstract ImmutableSet<String> removedKeys();

  public static ArtifactTrackerUpdateResult create(
      ImmutableSet<Path> updatedFiles, ImmutableSet<String> removedKeys) {
    return new AutoValue_ArtifactTrackerUpdateResult(updatedFiles, removedKeys);
  }
}
