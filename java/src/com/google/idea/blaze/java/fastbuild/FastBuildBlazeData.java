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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.aspect.FastBuildInfo;
import com.google.devtools.intellij.aspect.FastBuildInfo.Data;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  public abstract ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data();

  public abstract Optional<AndroidInfo> androidInfo();

  public abstract Optional<JavaInfo> javaInfo();

  public abstract Optional<JavaToolchainInfo> javaToolchainInfo();

  public static Builder builder() {
    return new AutoValue_FastBuildBlazeData.Builder()
        .setDependencies(ImmutableList.of())
        .setData(ImmutableMap.of());
  }

  /** A builder for {@link FastBuildBlazeData} objects. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLabel(Label label);

    public abstract Builder setWorkspaceName(String workspaceName);

    public abstract Builder setDependencies(Collection<Label> dependencies);

    public abstract Builder setData(Map<Label, ImmutableSet<ArtifactLocation>> data);

    public abstract Builder setAndroidInfo(AndroidInfo androidInfo);

    public abstract Builder setJavaInfo(JavaInfo javaInfo);

    public abstract Builder setJavaToolchainInfo(JavaToolchainInfo javaToolchainInfo);

    public abstract FastBuildBlazeData build();
  }

  static FastBuildBlazeData fromProto(FastBuildInfo.FastBuildBlazeData proto) {
    checkState(!Strings.isNullOrEmpty(proto.getWorkspaceName()), MISSING_WORKSPACE_NAME_ERROR);
    FastBuildBlazeData.Builder builder =
        FastBuildBlazeData.builder()
            .setLabel(Label.fromProto(proto.getLabel()))
            .setWorkspaceName(proto.getWorkspaceName())
            .setDependencies(
                proto.getDependenciesList().stream().map(Label::fromProto).collect(toSet()))
            .setData(convertDataToMap(proto.getDataList()));
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

  private static ImmutableMap<Label, ImmutableSet<ArtifactLocation>> convertDataToMap(
      List<Data> dataList) {
    return dataList.stream()
        .collect(
            toImmutableMap(
                data -> Label.fromProto(data.getLabel()),
                data ->
                    data.getArtifactsList().stream()
                        .map(ArtifactLocation::fromProto)
                        .collect(toImmutableSet())));
  }

  /** Data about an Android rule (android_library, android_roboelectric_test, etc.) */
  @AutoValue
  public abstract static class AndroidInfo {
    public abstract Optional<ArtifactLocation> aar();

    public abstract Optional<ArtifactLocation> mergedManifest();

    public static AndroidInfo create(
        @Nullable ArtifactLocation aar, @Nullable ArtifactLocation mergedManifest) {
      return new AutoValue_FastBuildBlazeData_AndroidInfo(
          Optional.ofNullable(aar), Optional.ofNullable(mergedManifest));
    }

    static AndroidInfo fromProto(FastBuildInfo.AndroidInfo proto) {
      ArtifactLocation aar = proto.hasAar() ? ArtifactLocation.fromProto(proto.getAar()) : null;
      ArtifactLocation manifest =
          proto.hasMergedManifest() ? ArtifactLocation.fromProto(proto.getMergedManifest()) : null;
      return create(aar, manifest);
    }
  }

  /** Data about a Java rule (java_library, java_test, etc.) */
  @AutoValue
  public abstract static class JavaInfo {
    public abstract ImmutableSet<ArtifactLocation> sources();

    public abstract Optional<String> testClass();

    public abstract Optional<String> testSize();

    public abstract Optional<Label> launcher();

    public abstract boolean swigdeps();

    public abstract ImmutableList<String> annotationProcessorClassNames();

    public abstract ImmutableList<ArtifactLocation> annotationProcessorClasspath();

    public abstract ImmutableList<String> jvmFlags();

    public abstract Optional<String> mainClass();

    public static JavaInfo fromProto(FastBuildInfo.JavaInfo proto) {
      Set<ArtifactLocation> sources =
          proto.getSourcesList().stream().map(ArtifactLocation::fromProto).collect(toSet());
      Label launcher = proto.getLauncher().isEmpty() ? null : Label.fromProto(proto.getLauncher());
      Set<ArtifactLocation> annotationProcessorClasspath =
          proto.getAnnotationProcessorClasspathList().stream()
              .map(ArtifactLocation::fromProto)
              .collect(toSet());
      return builder()
          .setSources(sources)
          .setTestClass(emptyToNull(proto.getTestClass()))
          .setTestSize(emptyToNull(proto.getTestSize()))
          .setLauncher(launcher)
          .setSwigdeps(proto.getSwigdeps())
          .setAnnotationProcessorClassNames(proto.getAnnotationProcessorClassNamesList())
          .setAnnotationProcessorClasspath(annotationProcessorClasspath)
          .setJvmFlags(proto.getJvmFlagsList())
          .setMainClass(proto.getMainClass())
          .build();
    }

    public static Builder builder() {
      return new AutoValue_FastBuildBlazeData_JavaInfo.Builder()
          .setSwigdeps(true)
          .setSources(ImmutableList.of())
          .setAnnotationProcessorClassNames(ImmutableList.of())
          .setAnnotationProcessorClasspath(ImmutableList.of())
          .setJvmFlags(ImmutableList.of());
    }

    /** A builder for {@link JavaInfo} objects. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setSources(Collection<ArtifactLocation> sources);

      public abstract Builder setTestClass(@Nullable String testClass);

      public abstract Builder setTestSize(@Nullable String testSize);

      public abstract Builder setLauncher(@Nullable Label label);

      public abstract Builder setSwigdeps(boolean swigdeps);

      public abstract Builder setAnnotationProcessorClassNames(
          Collection<String> annotationProcessorClassNames);

      public abstract Builder setAnnotationProcessorClasspath(
          Collection<ArtifactLocation> annotationProcessorClasspath);

      public abstract Builder setJvmFlags(Collection<String> jvmFlags);

      public abstract Builder setMainClass(@Nullable String mainClass);

      public abstract JavaInfo build();
    }
  }

  /** Data about a java_toolchain rule. */
  @AutoValue
  abstract static class JavaToolchainInfo {
    public abstract ImmutableList<ArtifactLocation> javacJars();

    public abstract ImmutableList<ArtifactLocation> bootClasspathJars();

    public abstract String sourceVersion();

    public abstract String targetVersion();

    static JavaToolchainInfo create(
        ImmutableList<ArtifactLocation> javacJars,
        ImmutableList<ArtifactLocation> bootJars,
        String sourceVersion,
        String targetVersion) {
      return new AutoValue_FastBuildBlazeData_JavaToolchainInfo(
          javacJars, bootJars, sourceVersion, targetVersion);
    }

    static JavaToolchainInfo fromProto(FastBuildInfo.JavaToolchainInfo javaToolchainInfo) {
      ImmutableList<ArtifactLocation> javacJars =
          javaToolchainInfo.getJavacJarsList().stream()
              .map(ArtifactLocation::fromProto)
              .collect(toImmutableList());
      ImmutableList<ArtifactLocation> bootJars =
          javaToolchainInfo.getBootclasspathJarsList().stream()
              .map(ArtifactLocation::fromProto)
              .collect(toImmutableList());
      return create(
          javacJars,
          bootJars,
          javaToolchainInfo.getSourceVersion(),
          javaToolchainInfo.getTargetVersion());
    }
  }
}
