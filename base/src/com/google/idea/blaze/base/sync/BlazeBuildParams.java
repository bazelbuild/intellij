/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.intellij.openapi.project.Project;

/** Parameters specific to blaze builds during sync. */
@AutoValue
public abstract class BlazeBuildParams {

  public static BlazeBuildParams fromProject(Project project) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    BuildBinaryType binaryType = provider.getSyncBinaryType();
    return builder()
        .setBlazeBinaryPath(provider.getSyncBinaryPath(project))
        .setBlazeBinaryType(binaryType)
        .setParallelizeBuilds(binaryType.isRemote)
        .build();
  }

  public abstract String blazeBinaryPath();

  public abstract BuildBinaryType blazeBinaryType();

  /**
   * Whether batched build invocations are run in parallel, when possible (only when building
   * remotely).
   */
  public abstract boolean parallelizeBuilds();

  public static Builder builder() {
    return new AutoValue_BlazeBuildParams.Builder();
  }

  /** Builder class for {@link BlazeBuildParams}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBlazeBinaryPath(String value);

    public abstract Builder setBlazeBinaryType(BuildBinaryType value);

    // not public; derived from BuildBinaryType
    abstract Builder setParallelizeBuilds(boolean parallelizeBuilds);

    abstract BuildBinaryType blazeBinaryType();

    abstract BlazeBuildParams autoBuild();

    public BlazeBuildParams build() {
      setParallelizeBuilds(blazeBinaryType().isRemote);
      return autoBuild();
    }
  }
}
