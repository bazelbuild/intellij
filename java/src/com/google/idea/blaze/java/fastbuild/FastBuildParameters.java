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
package com.google.idea.blaze.java.fastbuild;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;

/**
 * The parameters used to create a fast build.
 *
 * <p>If any of these parameters change, the full deploy jar will need to be rebuilt.
 */
@AutoValue
abstract class FastBuildParameters {

  abstract String blazeBinary();

  abstract ImmutableList<String> buildFlags();

  abstract ImmutableList<String> infoFlags();

  static Builder builder() {
    return new AutoValue_FastBuildParameters.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setBlazeBinary(String blazeBinary);

    abstract ImmutableList.Builder<String> buildFlagsBuilder();

    @CanIgnoreReturnValue
    Builder addBuildFlags(List<String> buildFlags) {
      buildFlagsBuilder().addAll(buildFlags);
      return this;
    }

    abstract ImmutableList.Builder<String> infoFlagsBuilder();

    @CanIgnoreReturnValue
    Builder addInfoFlags(List<String> infoFlags) {
      infoFlagsBuilder().addAll(infoFlags);
      return this;
    }

    abstract FastBuildParameters build();
  }
}
