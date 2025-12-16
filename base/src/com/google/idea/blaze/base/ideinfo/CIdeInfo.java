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

    private static final RuleContext EMPTY = builder().build();

    public abstract ImmutableList<ArtifactLocation> sources();

    public abstract ImmutableList<ArtifactLocation> headers();

    public abstract ImmutableList<ArtifactLocation> textualHeaders();

    public abstract ImmutableList<String> copts();

    public abstract ImmutableList<String> conlyopts();

    public abstract ImmutableList<String> cxxopts();

    public abstract ImmutableList<String> args();

    public abstract String includePrefix();

    public abstract String stripIncludePrefix();

    public static CIdeInfo.RuleContext fromProto(IntellijIdeInfo.CIdeInfo.RuleContext proto) {
      return builder()
          .setSources(ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto))
          .setHeaders(ProtoWrapper.map(proto.getHeadersList(), ArtifactLocation::fromProto))
          .setTextualHeaders(ProtoWrapper.map(proto.getTextualHeadersList(), ArtifactLocation::fromProto))
          .setCopts(ProtoWrapper.internStrings(proto.getCoptsList()))
          .setConlyopts(ProtoWrapper.internStrings(proto.getConlyoptsList()))
          .setCxxopts(ProtoWrapper.internStrings(proto.getCxxoptsList()))
          .setArgs(ProtoWrapper.internStrings(proto.getArgsList()))
          .setIncludePrefix(ProtoWrapper.internString(proto.getIncludePrefix()))
          .setStripIncludePrefix(ProtoWrapper.internString(proto.getStripIncludePrefix()))
          .build();
    }

    @Override
    public IntellijIdeInfo.CIdeInfo.RuleContext toProto() {
      return IntellijIdeInfo.CIdeInfo.RuleContext.newBuilder()
          .addAllSources(ProtoWrapper.mapToProtos(sources()))
          .addAllHeaders(ProtoWrapper.mapToProtos(headers()))
          .addAllTextualHeaders(ProtoWrapper.mapToProtos(textualHeaders()))
          .addAllCopts(copts())
          .addAllConlyopts(conlyopts())
          .addAllCxxopts(cxxopts())
          .addAllArgs(args())
          .setIncludePrefix(includePrefix())
          .setStripIncludePrefix(stripIncludePrefix())
          .build();
    }

    public static Builder builder() {
      return new AutoValue_CIdeInfo_RuleContext.Builder()
          .setSources(ImmutableList.of())
          .setHeaders(ImmutableList.of())
          .setTextualHeaders(ImmutableList.of())
          .setCopts(ImmutableList.of())
          .setConlyopts(ImmutableList.of())
          .setCxxopts(ImmutableList.of())
          .setArgs(ImmutableList.of())
          .setIncludePrefix("")
          .setStripIncludePrefix("");
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setSources(ImmutableList<ArtifactLocation> value);

      public abstract Builder setHeaders(ImmutableList<ArtifactLocation> value);

      public abstract Builder setTextualHeaders(ImmutableList<ArtifactLocation> value);

      public abstract Builder setCopts(ImmutableList<String> value);

      public abstract Builder setConlyopts(ImmutableList<String> value);

      public abstract Builder setCxxopts(ImmutableList<String> value);

      public abstract Builder setArgs(ImmutableList<String> value);

      public abstract Builder setIncludePrefix(String value);

      public abstract Builder setStripIncludePrefix(String value);

      public abstract RuleContext build();
    }
  }

  @AutoValue
  public static abstract class CompilationContext implements ProtoWrapper<IntellijIdeInfo.CIdeInfo.CompilationContext> {

    private static final CompilationContext EMPTY = builder().build();

    public abstract ImmutableList<ArtifactLocation> headers();

    public abstract ImmutableList<String> defines();

    public abstract ImmutableList<ExecutionRootPath> includes();

    public abstract ImmutableList<ExecutionRootPath> quoteIncludes();

    public abstract ImmutableList<ExecutionRootPath> systemIncludes();

    public static CIdeInfo.CompilationContext fromProto(IntellijIdeInfo.CIdeInfo.CompilationContext proto) {
      return builder()
          .setHeaders(ProtoWrapper.map(proto.getHeadersList(), ArtifactLocation::fromProto))
          .setDefines(ProtoWrapper.internStrings(proto.getDefinesList()))
          .setIncludes(ProtoWrapper.map(proto.getIncludesList(), ExecutionRootPath::fromProto))
          .setQuoteIncludes(ProtoWrapper.map(proto.getQuoteIncludesList(), ExecutionRootPath::fromProto))
          .setSystemIncludes(ProtoWrapper.map(proto.getSystemIncludesList(), ExecutionRootPath::fromProto))
          .build();
    }

    @Override
    public IntellijIdeInfo.CIdeInfo.CompilationContext toProto() {
      return IntellijIdeInfo.CIdeInfo.CompilationContext.newBuilder()
          .addAllHeaders(ProtoWrapper.mapToProtos(headers()))
          .addAllDefines(defines())
          .addAllIncludes(ProtoWrapper.mapToProtos(includes()))
          .addAllQuoteIncludes(ProtoWrapper.mapToProtos(quoteIncludes()))
          .addAllSystemIncludes(ProtoWrapper.mapToProtos(systemIncludes()))
          .build();
    }

    public static Builder builder() {
      return new AutoValue_CIdeInfo_CompilationContext.Builder()
          .setHeaders(ImmutableList.of())
          .setDefines(ImmutableList.of())
          .setIncludes(ImmutableList.of())
          .setQuoteIncludes(ImmutableList.of())
          .setSystemIncludes(ImmutableList.of());
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setHeaders(ImmutableList<ArtifactLocation> value);

      public abstract Builder setDefines(ImmutableList<String> value);

      public abstract Builder setIncludes(ImmutableList<ExecutionRootPath> value);

      public abstract Builder setQuoteIncludes(ImmutableList<ExecutionRootPath> value);

      public abstract Builder setSystemIncludes(ImmutableList<ExecutionRootPath> value);

      public abstract CompilationContext build();
    }
  }

  public abstract RuleContext ruleContext();

  public abstract CompilationContext compilationContext();

  public static CIdeInfo fromProto(IntellijIdeInfo.CIdeInfo proto) {
    final var builder = builder();

    if (proto.hasRuleContext()) {
      builder.setRuleContext(RuleContext.fromProto(proto.getRuleContext()));
    }
    if (proto.hasCompilationContext()) {
      builder.setCompilationContext(CompilationContext.fromProto(proto.getCompilationContext()));
    }

    return builder.build();
  }

  @Override
  public IntellijIdeInfo.CIdeInfo toProto() {
    return IntellijIdeInfo.CIdeInfo.newBuilder()
        .setCompilationContext(compilationContext().toProto())
        .setRuleContext(ruleContext().toProto())
        .build();
  }

  public static Builder builder() {
    return new AutoValue_CIdeInfo.Builder()
        .setRuleContext(RuleContext.EMPTY)
        .setCompilationContext(CompilationContext.EMPTY);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setRuleContext(RuleContext value);

    public abstract Builder setCompilationContext(CompilationContext value);

    public abstract CIdeInfo build();
  }
}
