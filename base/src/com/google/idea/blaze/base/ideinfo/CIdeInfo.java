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

  @AutoValue
  public static abstract class RuleContext implements ProtoWrapper<IntellijIdeInfo.CIdeInfo.RuleContext> {

    public abstract ImmutableList<ArtifactLocation> sources();

    public abstract ImmutableList<ArtifactLocation> headers();

    public abstract ImmutableList<ArtifactLocation> textualHeaders();

    public abstract ImmutableList<String> copts();

    public abstract String includePrefix();

    public abstract String stripIncludePrefix();

    public static CIdeInfo.RuleContext fromProto(IntellijIdeInfo.CIdeInfo.RuleContext proto) {
      return new AutoValue_CIdeInfo_RuleContext(
          ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto),
          ProtoWrapper.map(proto.getHeadersList(), ArtifactLocation::fromProto),
          ProtoWrapper.map(proto.getTextualHeadersList(), ArtifactLocation::fromProto),
          ProtoWrapper.internStrings(proto.getCoptsList()),
          ProtoWrapper.internString(proto.getIncludePrefix()),
          ProtoWrapper.internString(proto.getStripIncludePrefix())
      );
    }

    @Override
    public IntellijIdeInfo.CIdeInfo.RuleContext toProto() {
      return IntellijIdeInfo.CIdeInfo.RuleContext.newBuilder()
          .addAllSources(ProtoWrapper.mapToProtos(sources()))
          .addAllHeaders(ProtoWrapper.mapToProtos(headers()))
          .addAllTextualHeaders(ProtoWrapper.mapToProtos(textualHeaders()))
          .addAllCopts(copts())
          .setIncludePrefix(includePrefix())
          .setStripIncludePrefix(stripIncludePrefix())
          .build();
    }
  }

  @AutoValue
  public static abstract class CompilationContext implements ProtoWrapper<IntellijIdeInfo.CIdeInfo.CompilationContext> {

    public abstract ImmutableList<ArtifactLocation> directHeaders();

    public abstract ImmutableList<String> defines();

    public abstract ImmutableList<ExecutionRootPath> includes();

    public abstract ImmutableList<ExecutionRootPath> quoteIncludes();

    public abstract ImmutableList<ExecutionRootPath> systemIncludes();

    public static CIdeInfo.CompilationContext fromProto(IntellijIdeInfo.CIdeInfo.CompilationContext proto) {
      return new AutoValue_CIdeInfo_CompilationContext(
          ProtoWrapper.map(proto.getDirectHeadersList(), ArtifactLocation::fromProto),
          ProtoWrapper.internStrings(proto.getDefinesList()),
          ProtoWrapper.map(proto.getIncludesList(), ExecutionRootPath::fromProto),
          ProtoWrapper.map(proto.getQuoteIncludesList(), ExecutionRootPath::fromProto),
          ProtoWrapper.map(proto.getSystemIncludesList(), ExecutionRootPath::fromProto)
      );
    }

    @Override
    public IntellijIdeInfo.CIdeInfo.CompilationContext toProto() {
      return IntellijIdeInfo.CIdeInfo.CompilationContext.newBuilder()
          .addAllDirectHeaders(ProtoWrapper.mapToProtos(directHeaders()))
          .addAllDefines(defines())
          .addAllIncludes(ProtoWrapper.mapToProtos(includes()))
          .addAllQuoteIncludes(ProtoWrapper.mapToProtos(quoteIncludes()))
          .addAllSystemIncludes(ProtoWrapper.mapToProtos(systemIncludes()))
          .build();
    }
  }

  public abstract RuleContext ruleContext();

  public abstract CompilationContext compilationContext();

  public abstract ImmutableList<TargetKey> dependencies();

  static CIdeInfo fromProto(IntellijIdeInfo.CIdeInfo proto) {
    return new AutoValue_CIdeInfo(
        RuleContext.fromProto(proto.getRuleContext()),
        CompilationContext.fromProto(proto.getCompilationContext()),
        ProtoWrapper.map(proto.getDependenciesList(), TargetKey::fromProto)
    );
  }

  @Override
  public IntellijIdeInfo.CIdeInfo toProto() {
    final var builder = IntellijIdeInfo.CIdeInfo.newBuilder()
        .setCompilationContext(compilationContext().toProto())
        .addAllDependencies(ProtoWrapper.mapToProtos(dependencies()));

    if (ruleContext() != null) {
      builder.setRuleContext(ruleContext().toProto());
    }

    return builder.build();
  }
}
