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

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/** Sister class to {@link JavaIdeInfo} */
public final class CIdeInfo implements ProtoWrapper<IntellijIdeInfo.CIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;
  private final ImmutableList<ArtifactLocation> headers;
  private final ImmutableList<ArtifactLocation> textualHeaders;

  private final ImmutableList<String> localDefines;
  private final ImmutableList<ExecutionRootPath> localIncludeDirectories;
  // From the cpp compilation context provider.
  // These should all be for the entire transitive closure.
  private final ImmutableList<ExecutionRootPath> transitiveIncludeDirectories;
  private final ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories;
  private final ImmutableList<String> transitiveDefines;
  private final ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories;

  private CIdeInfo(
      ImmutableList<ArtifactLocation> sources,
      ImmutableList<ArtifactLocation> headers,
      ImmutableList<ArtifactLocation> textualHeaders,
      ImmutableList<String> localDefines,
      ImmutableList<ExecutionRootPath> localIncludeDirectories,
      ImmutableList<ExecutionRootPath> transitiveIncludeDirectories,
      ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories,
      ImmutableList<String> transitiveDefines,
      ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories) {
    this.sources = sources;
    this.headers = headers;
    this.textualHeaders = textualHeaders;
    this.localDefines = localDefines;
    this.localIncludeDirectories = localIncludeDirectories;
    this.transitiveIncludeDirectories = transitiveIncludeDirectories;
    this.transitiveQuoteIncludeDirectories = transitiveQuoteIncludeDirectories;
    this.transitiveDefines = transitiveDefines;
    this.transitiveSystemIncludeDirectories = transitiveSystemIncludeDirectories;
  }

  static CIdeInfo fromProto(IntellijIdeInfo.CIdeInfo proto) {
    proto = fixProto(proto);
    return new CIdeInfo(
        ProtoWrapper.map(proto.getSourceList(), ArtifactLocation::fromProto),
        ProtoWrapper.map(proto.getHeaderList(), ArtifactLocation::fromProto),
        ProtoWrapper.map(proto.getTextualHeaderList(), ArtifactLocation::fromProto),
        ImmutableList.copyOf(proto.getTargetDefineList()),
        ProtoWrapper.map(proto.getTargetIncludeList(), ExecutionRootPath::fromProto),
        ProtoWrapper.map(proto.getTransitiveIncludeDirectoryList(), ExecutionRootPath::fromProto),
        ProtoWrapper.map(
            proto.getTransitiveQuoteIncludeDirectoryList(), ExecutionRootPath::fromProto),
        ImmutableList.copyOf(proto.getTransitiveDefineList()),
        ProtoWrapper.map(
            proto.getTransitiveSystemIncludeDirectoryList(), ExecutionRootPath::fromProto));
  }

  @Override
  public IntellijIdeInfo.CIdeInfo toProto() {
    return IntellijIdeInfo.CIdeInfo.newBuilder()
        .addAllSource(ProtoWrapper.mapToProtos(sources))
        .addAllHeader(ProtoWrapper.mapToProtos(headers))
        .addAllTextualHeader(ProtoWrapper.mapToProtos(textualHeaders))
        .addAllTargetDefine(localDefines)
        .addAllTargetInclude(ProtoWrapper.mapToProtos(localIncludeDirectories))
        .addAllTransitiveIncludeDirectory(ProtoWrapper.mapToProtos(transitiveIncludeDirectories))
        .addAllTransitiveQuoteIncludeDirectory(
            ProtoWrapper.mapToProtos(transitiveQuoteIncludeDirectories))
        .addAllTransitiveDefine(transitiveDefines)
        .addAllTransitiveSystemIncludeDirectory(
            ProtoWrapper.mapToProtos(transitiveSystemIncludeDirectories))
        .build();
  }

  private static IntellijIdeInfo.CIdeInfo fixProto(IntellijIdeInfo.CIdeInfo proto) {
    if (!proto.getTargetCoptList().isEmpty()) {
      UnfilteredCompilerOptions compilerOptions =
          UnfilteredCompilerOptions.builder()
              .registerSingleOrSplitOption("-D")
              .registerSingleOrSplitOption("-I")
              .build(proto.getTargetCoptList());
      return proto
          .toBuilder()
          .addAllTargetDefine(compilerOptions.getExtractedOptionValues("-D"))
          .addAllTargetInclude(compilerOptions.getExtractedOptionValues("-I"))
          .clearTargetCopt()
          .build();
    }
    return proto;
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  public ImmutableList<ArtifactLocation> getHeaders() {
    return headers;
  }

  public ImmutableList<ArtifactLocation> getTextualHeaders() {
    return textualHeaders;
  }

  public ImmutableList<String> getLocalDefines() {
    return localDefines;
  }

  public ImmutableList<ExecutionRootPath> getLocalIncludeDirectories() {
    return localIncludeDirectories;
  }

  public ImmutableList<ExecutionRootPath> getTransitiveIncludeDirectories() {
    return transitiveIncludeDirectories;
  }

  public ImmutableList<ExecutionRootPath> getTransitiveQuoteIncludeDirectories() {
    return transitiveQuoteIncludeDirectories;
  }

  public ImmutableList<String> getTransitiveDefines() {
    return transitiveDefines;
  }

  public ImmutableList<ExecutionRootPath> getTransitiveSystemIncludeDirectories() {
    return transitiveSystemIncludeDirectories;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for c rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();
    private final ImmutableList.Builder<ArtifactLocation> headers = ImmutableList.builder();
    private final ImmutableList.Builder<ArtifactLocation> textualHeaders = ImmutableList.builder();

    private final ImmutableList.Builder<String> localDefines = ImmutableList.builder();
    private final ImmutableList.Builder<ExecutionRootPath> localIncludeDirectories =
        ImmutableList.builder();
    private final ImmutableList.Builder<ExecutionRootPath> transitiveIncludeDirectories =
        ImmutableList.builder();
    private final ImmutableList.Builder<ExecutionRootPath> transitiveQuoteIncludeDirectories =
        ImmutableList.builder();
    private final ImmutableList.Builder<String> transitiveDefines = ImmutableList.builder();
    private final ImmutableList.Builder<ExecutionRootPath> transitiveSystemIncludeDirectories =
        ImmutableList.builder();

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public Builder addSource(ArtifactLocation source) {
      this.sources.add(source);
      return this;
    }

    public Builder addHeaders(Iterable<ArtifactLocation> headers) {
      this.headers.addAll(headers);
      return this;
    }

    public Builder addHeader(ArtifactLocation header) {
      this.headers.add(header);
      return this;
    }

    public Builder addTextualHeaders(Iterable<ArtifactLocation> textualHeaders) {
      this.textualHeaders.addAll(textualHeaders);
      return this;
    }

    public Builder addTextualHeader(ArtifactLocation textualHeader) {
      this.textualHeaders.add(textualHeader);
      return this;
    }

    public Builder addLocalDefines(Iterable<String> localDefines) {
      this.localDefines.addAll(localDefines);
      return this;
    }

    public Builder addLocalIncludeDirectories(Iterable<ExecutionRootPath> localIncludeDirectories) {
      this.localIncludeDirectories.addAll(localIncludeDirectories);
      return this;
    }

    public Builder addTransitiveIncludeDirectories(
        Iterable<ExecutionRootPath> transitiveIncludeDirectories) {
      this.transitiveIncludeDirectories.addAll(transitiveIncludeDirectories);
      return this;
    }

    public Builder addTransitiveQuoteIncludeDirectories(
        Iterable<ExecutionRootPath> transitiveQuoteIncludeDirectories) {
      this.transitiveQuoteIncludeDirectories.addAll(transitiveQuoteIncludeDirectories);
      return this;
    }

    public Builder addTransitiveDefines(Iterable<String> transitiveDefines) {
      this.transitiveDefines.addAll(transitiveDefines);
      return this;
    }

    public Builder addTransitiveSystemIncludeDirectories(
        Iterable<ExecutionRootPath> transitiveSystemIncludeDirectories) {
      this.transitiveSystemIncludeDirectories.addAll(transitiveSystemIncludeDirectories);
      return this;
    }

    public CIdeInfo build() {
      return new CIdeInfo(
          sources.build(),
          headers.build(),
          textualHeaders.build(),
          localDefines.build(),
          localIncludeDirectories.build(),
          transitiveIncludeDirectories.build(),
          transitiveQuoteIncludeDirectories.build(),
          transitiveDefines.build(),
          transitiveSystemIncludeDirectories.build());
    }
  }

  @Override
  public String toString() {
    return "CIdeInfo{"
        + "\n"
        + "  sources="
        + getSources()
        + "\n"
        + "  headers="
        + getHeaders()
        + "\n"
        + "  textualHeaders="
        + getTextualHeaders()
        + "\n"
        + "  localDefines="
        + getLocalDefines()
        + "\n"
        + "  localIncludeDirectories="
        + getLocalIncludeDirectories()
        + "\n"
        + "  transitiveIncludeDirectories="
        + getTransitiveIncludeDirectories()
        + "\n"
        + "  transitiveQuoteIncludeDirectories="
        + getTransitiveQuoteIncludeDirectories()
        + "\n"
        + "  transitiveDefines="
        + getTransitiveDefines()
        + "\n"
        + "  transitiveSystemIncludeDirectories="
        + getTransitiveSystemIncludeDirectories()
        + "\n"
        + '}';
  }
}
