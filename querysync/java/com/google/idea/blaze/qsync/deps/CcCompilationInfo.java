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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.cc.CcIncludeDirectories;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * C/C++ compilation information. This stores information required to compile C or C++ targets. The
 * information is extracted from the build at build deps time.
 */
@AutoValue
abstract class CcCompilationInfo {
  abstract Label target();

  abstract ImmutableList<String> defines();

  abstract ImmutableList<ProjectPath> includeDirectories();

  abstract ImmutableList<ProjectPath> quoteIncludeDirectories();

  abstract ImmutableList<ProjectPath> systemIncludeDirectories();

  abstract ImmutableList<ProjectPath> frameworkIncludeDirectories();

  abstract ImmutableList<BuildArtifact> genHeaders();

  abstract String toolchainId();

  static CcCompilationInfo.Builder builder() {
    return new AutoValue_CcCompilationInfo.Builder();
  }

  static CcCompilationInfo create(CcTargetInfo targetInfo, Function<Path, String> digestMap) {
    Label target = Label.of(targetInfo.getLabel());
    return builder()
        .target(target)
        .defines(ImmutableList.copyOf(targetInfo.getDefinesList()))
        .includeDirectories(
            targetInfo.getIncludeDirectoriesList().stream()
                .map(CcIncludeDirectories::projectPathFor)
                .collect(toImmutableList()))
        .quoteIncludeDirectories(
            targetInfo.getQuoteIncludeDirectoriesList().stream()
                .map(CcIncludeDirectories::projectPathFor)
                .collect(toImmutableList()))
        .systemIncludeDirectories(
            targetInfo.getSystemIncludeDirectoriesList().stream()
                .map(CcIncludeDirectories::projectPathFor)
                .collect(toImmutableList()))
        .frameworkIncludeDirectories(
            targetInfo.getFrameworkIncludeDirectoriesList().stream()
                .map(CcIncludeDirectories::projectPathFor)
                .collect(toImmutableList()))
        .genHeaders(toArtifacts(targetInfo.getGenHdrsList(), digestMap, target))
        .toolchainId(targetInfo.getToolchainId())
        .build();
  }

  // TODO share this with the java class from where it was copied.
  private static ImmutableList<BuildArtifact> toArtifacts(
      List<String> paths, Function<Path, String> digestMap, Label target) {
    return paths.stream()
        .map(Interners::pathOf)
        .map(p -> BuildArtifact.create(p, target, digestMap))
        .collect(toImmutableList());
  }

  /** Builder for {@link CcCompilationInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder target(Label value);

    public abstract Builder defines(List<String> value);

    public abstract Builder includeDirectories(List<ProjectPath> value);

    public abstract Builder quoteIncludeDirectories(List<ProjectPath> value);

    public abstract Builder systemIncludeDirectories(List<ProjectPath> value);

    public abstract Builder frameworkIncludeDirectories(List<ProjectPath> value);

    public abstract Builder genHeaders(List<BuildArtifact> value);

    public abstract Builder toolchainId(String value);

    public abstract CcCompilationInfo build();
  }
}
