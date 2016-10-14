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
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Simple implementation of RuleIdeInfo. */
public final class RuleIdeInfo implements Serializable {
  private static final long serialVersionUID = 12L;

  public final RuleKey key;
  public final Label label;
  public final Kind kind;
  @Nullable public final ArtifactLocation buildFile;
  public final Collection<Label> dependencies;
  public final Collection<Label> runtimeDeps;
  public final Collection<String> tags;
  public final Collection<ArtifactLocation> sources;
  @Nullable public final CRuleIdeInfo cRuleIdeInfo;
  @Nullable public final CToolchainIdeInfo cToolchainIdeInfo;
  @Nullable public final JavaRuleIdeInfo javaRuleIdeInfo;
  @Nullable public final AndroidRuleIdeInfo androidRuleIdeInfo;
  @Nullable public final TestIdeInfo testIdeInfo;
  @Nullable public final ProtoLibraryLegacyInfo protoLibraryLegacyInfo;
  @Nullable public final JavaToolchainIdeInfo javaToolchainIdeInfo;

  public RuleIdeInfo(
      Label label,
      Kind kind,
      @Nullable ArtifactLocation buildFile,
      Collection<Label> dependencies,
      Collection<Label> runtimeDeps,
      Collection<String> tags,
      Collection<ArtifactLocation> sources,
      @Nullable CRuleIdeInfo cRuleIdeInfo,
      @Nullable CToolchainIdeInfo cToolchainIdeInfo,
      @Nullable JavaRuleIdeInfo javaRuleIdeInfo,
      @Nullable AndroidRuleIdeInfo androidRuleIdeInfo,
      @Nullable TestIdeInfo testIdeInfo,
      @Nullable ProtoLibraryLegacyInfo protoLibraryLegacyInfo,
      @Nullable JavaToolchainIdeInfo javaToolchainIdeInfo) {
    this.key = RuleKey.forPlainTarget(label);
    this.label = label;
    this.kind = kind;
    this.buildFile = buildFile;
    this.dependencies = dependencies;
    this.runtimeDeps = runtimeDeps;
    this.tags = tags;
    this.sources = sources;
    this.cRuleIdeInfo = cRuleIdeInfo;
    this.cToolchainIdeInfo = cToolchainIdeInfo;
    this.javaRuleIdeInfo = javaRuleIdeInfo;
    this.androidRuleIdeInfo = androidRuleIdeInfo;
    this.testIdeInfo = testIdeInfo;
    this.protoLibraryLegacyInfo = protoLibraryLegacyInfo;
    this.javaToolchainIdeInfo = javaToolchainIdeInfo;
  }

  @Override
  public String toString() {
    return label.toString();
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
    return true;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for rule ide info */
  public static class Builder {
    private Label label;
    private Kind kind;
    private ArtifactLocation buildFile;
    private final List<Label> dependencies = Lists.newArrayList();
    private final List<Label> runtimeDeps = Lists.newArrayList();
    private final List<String> tags = Lists.newArrayList();
    private final List<ArtifactLocation> sources = Lists.newArrayList();
    private final List<LibraryArtifact> libraries = Lists.newArrayList();
    private CRuleIdeInfo cRuleIdeInfo;
    private CToolchainIdeInfo cToolchainIdeInfo;
    private JavaRuleIdeInfo javaRuleIdeInfo;
    private AndroidRuleIdeInfo androidRuleIdeInfo;
    private TestIdeInfo testIdeInfo;
    private ProtoLibraryLegacyInfo protoLibraryLegacyInfo;
    private JavaToolchainIdeInfo javaToolchainIdeInfo;

    public Builder setLabel(String label) {
      return setLabel(new Label(label));
    }

    public Builder setLabel(Label label) {
      this.label = label;
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

    public Builder setJavaInfo(JavaRuleIdeInfo.Builder builder) {
      javaRuleIdeInfo = builder.build();
      return this;
    }

    public Builder setCInfo(CRuleIdeInfo cInfo) {
      this.cRuleIdeInfo = cInfo;
      return this;
    }

    public Builder setCInfo(CRuleIdeInfo.Builder cInfo) {
      return setCInfo(cInfo.build());
    }

    public Builder setCToolchainInfo(CToolchainIdeInfo info) {
      this.cToolchainIdeInfo = info;
      return this;
    }

    public Builder setCToolchainInfo(CToolchainIdeInfo.Builder info) {
      return setCToolchainInfo(info.build());
    }

    public Builder setAndroidInfo(AndroidRuleIdeInfo androidInfo) {
      this.androidRuleIdeInfo = androidInfo;
      return this;
    }

    public Builder setAndroidInfo(AndroidRuleIdeInfo.Builder androidInfo) {
      return setAndroidInfo(androidInfo.build());
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
      this.dependencies.add(label);
      return this;
    }

    public Builder addRuntimeDep(String s) {
      return addRuntimeDep(new Label(s));
    }

    public Builder addRuntimeDep(Label label) {
      this.runtimeDeps.add(label);
      return this;
    }

    public RuleIdeInfo build() {
      return new RuleIdeInfo(
          label,
          kind,
          buildFile,
          dependencies,
          runtimeDeps,
          tags,
          sources,
          cRuleIdeInfo,
          cToolchainIdeInfo,
          javaRuleIdeInfo,
          androidRuleIdeInfo,
          testIdeInfo,
          protoLibraryLegacyInfo,
          javaToolchainIdeInfo);
    }
  }
}
