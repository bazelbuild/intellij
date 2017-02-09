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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.Dependency.DependencyType;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Simple implementation of TargetIdeInfo. */
public final class TargetIdeInfo implements Serializable {
  private static final long serialVersionUID = 15L;

  public final TargetKey key;
  public final Kind kind;
  @Nullable public final ArtifactLocation buildFile;
  public final Collection<Dependency> dependencies;
  public final Collection<String> tags;
  public final Collection<ArtifactLocation> sources;
  @Nullable public final CIdeInfo cIdeInfo;
  @Nullable public final CToolchainIdeInfo cToolchainIdeInfo;
  @Nullable public final JavaIdeInfo javaIdeInfo;
  @Nullable public final AndroidIdeInfo androidIdeInfo;
  @Nullable public final PyIdeInfo pyIdeInfo;
  @Nullable public final TestIdeInfo testIdeInfo;
  @Nullable public final ProtoLibraryLegacyInfo protoLibraryLegacyInfo;
  @Nullable public final JavaToolchainIdeInfo javaToolchainIdeInfo;
  @Nullable public final IntellijPluginDeployInfo intellijPluginDeployInfo;

  public TargetIdeInfo(
      TargetKey key,
      Kind kind,
      @Nullable ArtifactLocation buildFile,
      Collection<Dependency> dependencies,
      Collection<String> tags,
      Collection<ArtifactLocation> sources,
      @Nullable CIdeInfo cIdeInfo,
      @Nullable CToolchainIdeInfo cToolchainIdeInfo,
      @Nullable JavaIdeInfo javaIdeInfo,
      @Nullable AndroidIdeInfo androidIdeInfo,
      @Nullable PyIdeInfo pyIdeInfo,
      @Nullable TestIdeInfo testIdeInfo,
      @Nullable ProtoLibraryLegacyInfo protoLibraryLegacyInfo,
      @Nullable JavaToolchainIdeInfo javaToolchainIdeInfo,
      @Nullable IntellijPluginDeployInfo intellijPluginDeployInfo) {
    this.key = key;
    this.kind = kind;
    this.buildFile = buildFile;
    this.dependencies = dependencies;
    this.tags = tags;
    this.sources = sources;
    this.cIdeInfo = cIdeInfo;
    this.cToolchainIdeInfo = cToolchainIdeInfo;
    this.javaIdeInfo = javaIdeInfo;
    this.androidIdeInfo = androidIdeInfo;
    this.pyIdeInfo = pyIdeInfo;
    this.testIdeInfo = testIdeInfo;
    this.protoLibraryLegacyInfo = protoLibraryLegacyInfo;
    this.javaToolchainIdeInfo = javaToolchainIdeInfo;
    this.intellijPluginDeployInfo = intellijPluginDeployInfo;
  }

  @Override
  public String toString() {
    return key.toString();
  }

  /** Returns whether this rule is one of the kinds. */
  public boolean kindIsOneOf(Kind... kinds) {
    return kindIsOneOf(Arrays.asList(kinds));
  }

  /** Returns whether this rule is one of the kinds. */
  public boolean kindIsOneOf(List<Kind> kinds) {
    if (kind != null) {
      return kind.isOneOf(kinds);
    }
    return false;
  }

  public boolean isPlainTarget() {
    return key.isPlainTarget();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for rule ide info */
  public static class Builder {
    private TargetKey key;
    private Kind kind;
    private ArtifactLocation buildFile;
    private final List<Dependency> dependencies = Lists.newArrayList();
    private final List<String> tags = Lists.newArrayList();
    private final List<ArtifactLocation> sources = Lists.newArrayList();
    private CIdeInfo cIdeInfo;
    private CToolchainIdeInfo cToolchainIdeInfo;
    private JavaIdeInfo javaIdeInfo;
    private AndroidIdeInfo androidIdeInfo;
    private PyIdeInfo pyIdeInfo;
    private TestIdeInfo testIdeInfo;
    private ProtoLibraryLegacyInfo protoLibraryLegacyInfo;
    private JavaToolchainIdeInfo javaToolchainIdeInfo;

    public Builder setLabel(String label) {
      return setLabel(new Label(label));
    }

    public Builder setLabel(Label label) {
      this.key = TargetKey.forPlainTarget(label);
      return this;
    }

    public Builder setBuildFile(ArtifactLocation buildFile) {
      this.buildFile = buildFile;
      return this;
    }

    public Builder setKind(String kind) {
      return setKind(Kind.fromString(kind));
    }

    public Builder setKind(Kind kind) {
      this.kind = kind;
      return this;
    }

    public Builder addSource(ArtifactLocation source) {
      this.sources.add(source);
      return this;
    }

    public Builder addSource(ArtifactLocation.Builder source) {
      return addSource(source.build());
    }

    public Builder setJavaInfo(JavaIdeInfo.Builder builder) {
      javaIdeInfo = builder.build();
      return this;
    }

    public Builder setCInfo(CIdeInfo cInfo) {
      this.cIdeInfo = cInfo;
      return this;
    }

    public Builder setCInfo(CIdeInfo.Builder cInfo) {
      return setCInfo(cInfo.build());
    }

    public Builder setCToolchainInfo(CToolchainIdeInfo info) {
      this.cToolchainIdeInfo = info;
      return this;
    }

    public Builder setCToolchainInfo(CToolchainIdeInfo.Builder info) {
      return setCToolchainInfo(info.build());
    }

    public Builder setAndroidInfo(AndroidIdeInfo androidInfo) {
      this.androidIdeInfo = androidInfo;
      return this;
    }

    public Builder setAndroidInfo(AndroidIdeInfo.Builder androidInfo) {
      return setAndroidInfo(androidInfo.build());
    }

    public Builder setPyInfo(PyIdeInfo.Builder pyInfo) {
      this.pyIdeInfo = pyInfo.build();
      return this;
    }

    public Builder setTestInfo(TestIdeInfo.Builder testInfo) {
      this.testIdeInfo = testInfo.build();
      return this;
    }

    public Builder setProtoLibraryLegacyInfo(
        ProtoLibraryLegacyInfo.Builder protoLibraryLegacyInfo) {
      this.protoLibraryLegacyInfo = protoLibraryLegacyInfo.build();
      return this;
    }

    public Builder setJavaToolchainIdeInfo(JavaToolchainIdeInfo.Builder javaToolchainIdeInfo) {
      this.javaToolchainIdeInfo = javaToolchainIdeInfo.build();
      return this;
    }

    public Builder addTag(String s) {
      this.tags.add(s);
      return this;
    }

    public Builder addDependency(String s) {
      return addDependency(new Label(s));
    }

    public Builder addDependency(Label label) {
      this.dependencies.add(
          new Dependency(TargetKey.forPlainTarget(label), DependencyType.COMPILE_TIME));
      return this;
    }

    public Builder addRuntimeDep(String s) {
      return addRuntimeDep(new Label(s));
    }

    public Builder addRuntimeDep(Label label) {
      this.dependencies.add(
          new Dependency(TargetKey.forPlainTarget(label), DependencyType.RUNTIME));
      return this;
    }

    public TargetIdeInfo build() {
      return new TargetIdeInfo(
          key,
          kind,
          buildFile,
          dependencies,
          tags,
          sources,
          cIdeInfo,
          cToolchainIdeInfo,
          javaIdeInfo,
          androidIdeInfo,
          pyIdeInfo,
          testIdeInfo,
          protoLibraryLegacyInfo,
          javaToolchainIdeInfo,
          null);
    }
  }
}
