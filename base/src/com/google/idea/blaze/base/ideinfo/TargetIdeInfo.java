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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.dependencies.TargetInfo;
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
  private static final long serialVersionUID = 19L;

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
  @Nullable public final AndroidSdkIdeInfo androidSdkIdeInfo;
  @Nullable public final AndroidAarIdeInfo androidAarIdeInfo;
  @Nullable public final PyIdeInfo pyIdeInfo;
  @Nullable public final GoIdeInfo goIdeInfo;
  @Nullable public final JsIdeInfo jsIdeInfo;
  @Nullable public final TsIdeInfo tsIdeInfo;
  @Nullable public final DartIdeInfo dartIdeInfo;
  @Nullable public final TestIdeInfo testIdeInfo;
  @Nullable public final JavaToolchainIdeInfo javaToolchainIdeInfo;
  @Nullable public final KotlinToolchainIdeInfo kotlinToolchainIdeInfo;

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
      @Nullable AndroidSdkIdeInfo androidSdkIdeInfo,
      @Nullable AndroidAarIdeInfo androidAarIdeInfo,
      @Nullable PyIdeInfo pyIdeInfo,
      @Nullable GoIdeInfo goIdeInfo,
      @Nullable JsIdeInfo jsIdeInfo,
      @Nullable TsIdeInfo tsIdeInfo,
      @Nullable DartIdeInfo dartIdeInfo,
      @Nullable TestIdeInfo testIdeInfo,
      @Nullable JavaToolchainIdeInfo javaToolchainIdeInfo,
      @Nullable KotlinToolchainIdeInfo kotlinToolchainIdeInfo) {
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
    this.androidSdkIdeInfo = androidSdkIdeInfo;
    this.androidAarIdeInfo = androidAarIdeInfo;
    this.pyIdeInfo = pyIdeInfo;
    this.goIdeInfo = goIdeInfo;
    this.jsIdeInfo = jsIdeInfo;
    this.tsIdeInfo = tsIdeInfo;
    this.dartIdeInfo = dartIdeInfo;
    this.testIdeInfo = testIdeInfo;
    this.javaToolchainIdeInfo = javaToolchainIdeInfo;
    this.kotlinToolchainIdeInfo = kotlinToolchainIdeInfo;
  }

  public TargetInfo toTargetInfo() {
    return TargetInfo.builder(key.label, kind.toString())
        .setTestSize(testIdeInfo != null ? testIdeInfo.testSize : null)
        .setSources(ImmutableList.copyOf(sources))
        .build();
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
  public boolean kindIsOneOf(Collection<Kind> kinds) {
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
    private AndroidAarIdeInfo androidAarIdeInfo;
    private PyIdeInfo pyIdeInfo;
    private GoIdeInfo goIdeInfo;
    private JsIdeInfo jsIdeInfo;
    private TsIdeInfo tsIdeInfo;
    private DartIdeInfo dartIdeInfo;
    private TestIdeInfo testIdeInfo;
    private JavaToolchainIdeInfo javaToolchainIdeInfo;
    private KotlinToolchainIdeInfo kotlinToolchainIdeInfo;

    public Builder setLabel(String label) {
      return setLabel(Label.create(label));
    }

    public Builder setLabel(Label label) {
      this.key = TargetKey.forPlainTarget(label);
      return this;
    }

    public Builder setBuildFile(ArtifactLocation buildFile) {
      this.buildFile = buildFile;
      return this;
    }

    @VisibleForTesting
    public Builder setKind(String kindString) {
      Kind kind = Preconditions.checkNotNull(Kind.fromString(kindString));
      return setKind(kind);
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

    public Builder setCInfo(CIdeInfo.Builder cInfoBuilder) {
      this.cIdeInfo = cInfoBuilder.build();
      this.sources.addAll(cIdeInfo.sources);
      this.sources.addAll(cIdeInfo.headers);
      this.sources.addAll(cIdeInfo.textualHeaders);
      return this;
    }

    public Builder setCToolchainInfo(CToolchainIdeInfo.Builder info) {
      this.cToolchainIdeInfo = info.build();
      return this;
    }

    public Builder setAndroidInfo(AndroidIdeInfo.Builder androidInfo) {
      this.androidIdeInfo = androidInfo.build();
      return this;
    }

    public Builder setAndroidAarInfo(AndroidAarIdeInfo aarInfo) {
      this.androidAarIdeInfo = aarInfo;
      return this;
    }

    public Builder setPyInfo(PyIdeInfo.Builder pyInfo) {
      this.pyIdeInfo = pyInfo.build();
      return this;
    }

    public Builder setGoInfo(GoIdeInfo.Builder goInfo) {
      this.goIdeInfo = goInfo.build();
      return this;
    }

    public Builder setJsInfo(JsIdeInfo.Builder jsInfo) {
      this.jsIdeInfo = jsInfo.build();
      return this;
    }

    public Builder setTsInfo(TsIdeInfo.Builder tsInfo) {
      this.tsIdeInfo = tsInfo.build();
      return this;
    }

    public Builder setDartInfo(DartIdeInfo.Builder dartInfo) {
      this.dartIdeInfo = dartInfo.build();
      return this;
    }

    public Builder setTestInfo(TestIdeInfo.Builder testInfo) {
      this.testIdeInfo = testInfo.build();
      return this;
    }

    public Builder setJavaToolchainIdeInfo(JavaToolchainIdeInfo.Builder javaToolchainIdeInfo) {
      this.javaToolchainIdeInfo = javaToolchainIdeInfo.build();
      return this;
    }

    public Builder setKotlinToolchainIdeInfo(KotlinToolchainIdeInfo.Builder toolchain) {
      this.kotlinToolchainIdeInfo = toolchain.build();
      return this;
    }

    public Builder addTag(String s) {
      this.tags.add(s);
      return this;
    }

    public Builder addDependency(String s) {
      return addDependency(Label.create(s));
    }

    public Builder addDependency(Label label) {
      this.dependencies.add(
          new Dependency(TargetKey.forPlainTarget(label), DependencyType.COMPILE_TIME));
      return this;
    }

    public Builder addRuntimeDep(String s) {
      return addRuntimeDep(Label.create(s));
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
          null,
          androidAarIdeInfo,
          pyIdeInfo,
          goIdeInfo,
          jsIdeInfo,
          tsIdeInfo,
          dartIdeInfo,
          testIdeInfo,
          javaToolchainIdeInfo,
          kotlinToolchainIdeInfo);
    }
  }
}
