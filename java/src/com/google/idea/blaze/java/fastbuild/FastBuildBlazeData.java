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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.aspect.FastBuildInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.ArtifactLocationFromProtobuf;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Data gathered from Blaze about a single target in a fast build's dependency tree. */
@AutoValue
public abstract class FastBuildBlazeData {

  private static final String MISSING_WORKSPACE_NAME_ERROR =
      "Error reading workspace name from fast build aspect.";

  public abstract Label label();

  public abstract String workspaceName();

  public abstract ImmutableSet<Label> dependencies();

  public abstract Optional<AndroidInfo> androidInfo();

  public abstract Optional<JavaInfo> javaInfo();

  public abstract Optional<JavaToolchainInfo> javaToolchainInfo();

  static Builder builder() {
    return new AutoValue_FastBuildBlazeData.Builder().setDependencies(ImmutableList.of());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setLabel(Label label);

    abstract Builder setWorkspaceName(String workspaceName);

    abstract Builder setDependencies(Collection<Label> dependencies);

    abstract Builder setAndroidInfo(AndroidInfo androidInfo);

    abstract Builder setJavaInfo(JavaInfo javaInfo);

    abstract Builder setJavaToolchainInfo(JavaToolchainInfo javaToolchainInfo);

    abstract FastBuildBlazeData build();
  }

  static FastBuildBlazeData fromProto(FastBuildInfo.FastBuildBlazeData proto) {
    checkState(!Strings.isNullOrEmpty(proto.getWorkspaceName()), MISSING_WORKSPACE_NAME_ERROR);
    FastBuildBlazeData.Builder builder =
        FastBuildBlazeData.builder()
            .setLabel(Label.create(proto.getLabel()))
            .setWorkspaceName(proto.getWorkspaceName())
            .setDependencies(
                proto.getDependenciesList().stream().map(Label::create).collect(toSet()));
    if (proto.hasAndroidInfo()) {
      builder.setAndroidInfo(AndroidInfo.fromProto(proto.getAndroidInfo()));
    }
    if (proto.hasJavaInfo()) {
      builder.setJavaInfo(JavaInfo.fromProto(proto.getJavaInfo()));
    }
    if (proto.hasJavaToolchainInfo()) {
      builder.setJavaToolchainInfo(JavaToolchainInfo.fromProto(proto.getJavaToolchainInfo()));
    }
    return builder.build();
  }

  /** Data about an Android rule (android_library, android_roboelectric_test, etc.) */
  @AutoValue
  public abstract static class AndroidInfo {
    public abstract Optional<ArtifactLocation> aar();

    public abstract Optional<ArtifactLocation> mergedManifest();

    static AndroidInfo create(
        @Nullable ArtifactLocation aar, @Nullable ArtifactLocation mergedManifest) {
      return new AutoValue_FastBuildBlazeData_AndroidInfo(
          Optional.ofNullable(aar), Optional.ofNullable(mergedManifest));
    }

    static AndroidInfo fromProto(FastBuildInfo.AndroidInfo proto) {
      ArtifactLocation aar =
          proto.hasAar() ? ArtifactLocationFromProtobuf.makeArtifactLocation(proto.getAar()) : null;
      ArtifactLocation manifest =
          proto.hasMergedManifest()
              ? ArtifactLocationFromProtobuf.makeArtifactLocation(proto.getMergedManifest())
              : null;
      return create(aar, manifest);
    }
  }

  /** Data about a Java rule (java_library, java_test, etc.) */
  @AutoValue
  public abstract static class JavaInfo {
    public abstract ImmutableSet<ArtifactLocation> sources();

    public abstract Optional<String> testClass();

    public abstract ImmutableList<String> annotationProcessorClassNames();

    public abstract ImmutableList<ArtifactLocation> annotationProcessorClasspath();

    public abstract ImmutableList<String> jvmFlags();

    static JavaInfo create(
        Collection<ArtifactLocation> sources,
        @Nullable String testClass,
        Collection<String> annotationProcessorClassNames,
        Collection<ArtifactLocation> annotationProcessorClassPath,
        Collection<String> jvmFlags) {
      return new AutoValue_FastBuildBlazeData_JavaInfo(
          ImmutableSet.copyOf(sources),
          Optional.ofNullable(testClass),
          ImmutableList.copyOf(annotationProcessorClassNames),
          ImmutableList.copyOf(annotationProcessorClassPath),
          ImmutableList.copyOf(jvmFlags));
    }

    static JavaInfo fromProto(FastBuildInfo.JavaInfo proto) {
      Set<ArtifactLocation> sources =
          proto
              .getSourcesList()
              .stream()
              .map(ArtifactLocationFromProtobuf::makeArtifactLocation)
              .collect(toSet());
      Set<ArtifactLocation> annotationProcessorClasspath =
          proto
              .getAnnotationProcessorClasspathList()
              .stream()
              .map(ArtifactLocationFromProtobuf::makeArtifactLocation)
              .collect(toSet());
      return create(
          sources,
          emptyToNull(proto.getTestClass()),
          proto.getAnnotationProcessorClassNamesList(),
          annotationProcessorClasspath,
          proto.getJvmFlagsList());
    }
  }

  /** Data about a java_toolchain rule. */
  @AutoValue
  abstract static class JavaToolchainInfo {
    public abstract ArtifactLocation javacJar();

    public abstract String sourceVersion();

    public abstract String targetVersion();

    static JavaToolchainInfo create(
        ArtifactLocation javacJar, String sourceVersion, String targetVersion) {
      return new AutoValue_FastBuildBlazeData_JavaToolchainInfo(
          javacJar, sourceVersion, targetVersion);
    }

    static JavaToolchainInfo fromProto(FastBuildInfo.JavaToolchainInfo javaToolchainInfo) {
      return create(
          ArtifactLocationFromProtobuf.makeArtifactLocation(javaToolchainInfo.getJavacJar()),
          javaToolchainInfo.getSourceVersion(),
          javaToolchainInfo.getTargetVersion());
    }
  }
}
