/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/** Data for clustering {@link BlazeResolveConfiguration} by "equivalence". */
@AutoValue
public abstract class BlazeResolveConfigurationData {

  public abstract BlazeCompilerSettings compilerSettings();
  public abstract CToolchainIdeInfo toolchainIdeInfo();

  // Everything from CIdeInfo except for sources, headers, etc.
  // That is parts that influence the flags, but not the actual input files.
  public abstract ImmutableList<String> localCopts();

  // From the cpp compilation context provider.
  // These should all be for the entire transitive closure.
  public abstract ImmutableList<ExecutionRootPath> transitiveIncludeDirectories();
  public abstract ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories();

  public abstract ImmutableList<String> transitiveDefines();
  public abstract ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories();

  static BlazeResolveConfigurationData create(
      CIdeInfo cIdeInfo,
      CToolchainIdeInfo toolchainIdeInfo,
      BlazeCompilerSettings compilerSettings) {
    return builder()
        .setCompilerSettings(compilerSettings)
        .setToolchainIdeInfo(toolchainIdeInfo)
        .setLocalCopts(cIdeInfo.localCopts())
        .setTransitiveIncludeDirectories(cIdeInfo.transitiveIncludeDirectories())
        .setTransitiveQuoteIncludeDirectories(cIdeInfo.transitiveQuoteIncludeDirectories())
        .setTransitiveDefines(cIdeInfo.transitiveDefines())
        .setTransitiveSystemIncludeDirectories(cIdeInfo.transitiveSystemIncludeDirectories())
        .build();
  }

  public static Builder builder() {
    return new AutoValue_BlazeResolveConfigurationData.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCompilerSettings(BlazeCompilerSettings value);

    public abstract Builder setToolchainIdeInfo(CToolchainIdeInfo value);

    public abstract Builder setLocalCopts(ImmutableList<String> value);

    public abstract Builder setTransitiveIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setTransitiveQuoteIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setTransitiveDefines(ImmutableList<String> value);

    public abstract Builder setTransitiveSystemIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract BlazeResolveConfigurationData build();
  }
}
