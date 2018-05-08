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

import com.google.auto.value.AutoOneOf;
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

  public abstract ProviderInfo providerInfo();

  static FastBuildBlazeData create(
      Label label,
      String workspaceName,
      Collection<Label> dependencies,
      ProviderInfo providerInfo) {
    return new AutoValue_FastBuildBlazeData(
        label, workspaceName, ImmutableSet.copyOf(dependencies), providerInfo);
  }

  static FastBuildBlazeData fromProto(FastBuildInfo.FastBuildBlazeData proto) {
    checkState(!Strings.isNullOrEmpty(proto.getWorkspaceName()), MISSING_WORKSPACE_NAME_ERROR);
    ProviderInfo providerInfo = null;
    switch (proto.getProviderInfoCase()) {
      case JAVA_INFO:
        providerInfo = ProviderInfo.ofJavaInfo(JavaInfo.fromProto(proto.getJavaInfo()));
        break;
      case JAVA_TOOLCHAIN_INFO:
        providerInfo =
            ProviderInfo.ofJavaToolchainInfo(
                JavaToolchainInfo.fromProto(proto.getJavaToolchainInfo()));
        break;
      case PROVIDERINFO_NOT_SET:
        throw new IllegalStateException("Unknown ProviderInfo type for label " + proto.getLabel());
    }
    return create(
        Label.create(proto.getLabel()),
        proto.getWorkspaceName(),
        proto.getDependenciesList().stream().map(Label::create).collect(toSet()),
        providerInfo);
  }

  /** Provider-specific data about this target. */
  @AutoOneOf(ProviderInfo.Type.class)
  public abstract static class ProviderInfo {
    /** The type of provider info contained within. */
    public enum Type {
      JAVA_INFO,
      JAVA_TOOLCHAIN_INFO
    }

    public abstract Type type();

    public abstract JavaInfo javaInfo();

    public abstract JavaToolchainInfo javaToolchainInfo();

    static ProviderInfo ofJavaInfo(JavaInfo javaInfo) {
      return AutoOneOf_FastBuildBlazeData_ProviderInfo.javaInfo(javaInfo);
    }

    static ProviderInfo ofJavaToolchainInfo(JavaToolchainInfo javaToolchainInfo) {
      return AutoOneOf_FastBuildBlazeData_ProviderInfo.javaToolchainInfo(javaToolchainInfo);
    }
  }

  /** Data about a Java rule (java_library, java_test, etc.) */
  @AutoValue
  public abstract static class JavaInfo {
    public abstract ImmutableSet<ArtifactLocation> sources();

    public abstract Optional<String> testClass();

    public abstract ImmutableList<String> annotationProcessorClassNames();

    public abstract ImmutableList<ArtifactLocation> annotationProcessorClasspath();

    static JavaInfo create(
        Collection<ArtifactLocation> sources,
        @Nullable String testClass,
        Collection<String> annotationProcessorClassNames,
        Collection<ArtifactLocation> annotationProcessorClassPath) {
      return new AutoValue_FastBuildBlazeData_JavaInfo(
          ImmutableSet.copyOf(sources),
          Optional.ofNullable(testClass),
          ImmutableList.copyOf(annotationProcessorClassNames),
          ImmutableList.copyOf(annotationProcessorClassPath));
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
          annotationProcessorClasspath);
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
