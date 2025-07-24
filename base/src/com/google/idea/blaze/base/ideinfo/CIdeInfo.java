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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/**
 * Ide info specific to cc rules.
 */
@AutoValue
public abstract class CIdeInfo implements ProtoWrapper<IntellijIdeInfo.CIdeInfo> {

  public abstract ImmutableList<ArtifactLocation> sources();

  public abstract ImmutableList<ArtifactLocation> headers();

  public abstract ImmutableList<ArtifactLocation> textualHeaders();

  public abstract ImmutableList<String> localCopts();

  public abstract ImmutableList<ExecutionRootPath> transitiveIncludeDirectories();

  public abstract ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories();

  public abstract ImmutableList<String> transitiveDefines();

  public abstract ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories();

  public abstract ImmutableList<TargetKey> transitiveDependencies();

  public abstract String includePrefix();

  public abstract String stripIncludePrefix();

  static CIdeInfo fromProto(IntellijIdeInfo.CIdeInfo proto) {
    return CIdeInfo.builder()
        .setSources(ProtoWrapper.map(proto.getSourceList(), ArtifactLocation::fromProto))
        .setHeaders(ProtoWrapper.map(proto.getHeaderList(), ArtifactLocation::fromProto))
        .setTextualHeaders(ProtoWrapper.map(proto.getTextualHeaderList(), ArtifactLocation::fromProto))
        .setLocalCopts(ProtoWrapper.internStrings(proto.getTargetCoptList()))
        .setTransitiveDefines(ProtoWrapper.internStrings(proto.getTransitiveDefineList()))
        .setTransitiveIncludeDirectories(
            ProtoWrapper.map(proto.getTransitiveIncludeDirectoryList(), ExecutionRootPath::fromProto))
        .setTransitiveQuoteIncludeDirectories(
            ProtoWrapper.map(proto.getTransitiveQuoteIncludeDirectoryList(), ExecutionRootPath::fromProto))
        .setTransitiveSystemIncludeDirectories(
            ProtoWrapper.map(proto.getTransitiveSystemIncludeDirectoryList(), ExecutionRootPath::fromProto))
        .setIncludePrefix(proto.getIncludePrefix())
        .setStripIncludePrefix(proto.getStripIncludePrefix())
        .setTransitiveDependencies(ProtoWrapper.map(proto.getTransitiveDependenciesList(), TargetKey::fromProto))
        .build();
  }

  @Override
  public IntellijIdeInfo.CIdeInfo toProto() {
    return IntellijIdeInfo.CIdeInfo.newBuilder()
        .addAllSource(ProtoWrapper.mapToProtos(sources()))
        .addAllHeader(ProtoWrapper.mapToProtos(headers()))
        .addAllTextualHeader(ProtoWrapper.mapToProtos(textualHeaders()))
        .addAllTargetCopt(localCopts())
        .addAllTransitiveIncludeDirectory(ProtoWrapper.mapToProtos(transitiveIncludeDirectories()))
        .addAllTransitiveQuoteIncludeDirectory(ProtoWrapper.mapToProtos(transitiveQuoteIncludeDirectories()))
        .addAllTransitiveDefine(transitiveDefines())
        .addAllTransitiveSystemIncludeDirectory(ProtoWrapper.mapToProtos(transitiveSystemIncludeDirectories()))
        .setIncludePrefix(includePrefix())
        .setStripIncludePrefix(stripIncludePrefix())
        .addAllTransitiveDependencies(ProtoWrapper.mapToProtos(transitiveDependencies()))
        .build();
  }

  public static Builder builder() {
    return new AutoValue_CIdeInfo.Builder();
  }

  /**
   * Builder for c rule info
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSources(ImmutableList<ArtifactLocation> value);

    public abstract Builder setHeaders(ImmutableList<ArtifactLocation> value);

    public abstract Builder setTextualHeaders(ImmutableList<ArtifactLocation> value);

    public abstract Builder setLocalCopts(ImmutableList<String> value);

    public abstract Builder setTransitiveIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setTransitiveQuoteIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setTransitiveDefines(ImmutableList<String> value);

    public abstract Builder setTransitiveSystemIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setTransitiveDependencies(ImmutableList<TargetKey> value);

    public abstract Builder setIncludePrefix(String value);

    public abstract Builder setStripIncludePrefix(String value);

    public abstract CIdeInfo build();
  }
}
