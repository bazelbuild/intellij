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
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import java.io.Serializable;

/** Sister class to {@link JavaIdeInfo} */
public class CIdeInfo implements Serializable {
  private static final long serialVersionUID = 8L;

  public final ImmutableList<ArtifactLocation> sources;
  public final ImmutableList<ArtifactLocation> headers;
  public final ImmutableList<ArtifactLocation> textualHeaders;

  public final ImmutableList<String> localDefines;
  public final ImmutableList<ExecutionRootPath> localIncludeDirectories;
  // From the cpp compilation context provider.
  // These should all be for the entire transitive closure.
  public final ImmutableList<ExecutionRootPath> transitiveIncludeDirectories;
  public final ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories;
  public final ImmutableList<String> transitiveDefines;
  public final ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories;

  public CIdeInfo(
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
        + sources
        + "\n"
        + "  headers="
        + headers
        + "\n"
        + "  textualHeaders="
        + textualHeaders
        + "\n"
        + "  localDefines="
        + localDefines
        + "\n"
        + "  localIncludeDirectories="
        + localIncludeDirectories
        + "\n"
        + "  transitiveIncludeDirectories="
        + transitiveIncludeDirectories
        + "\n"
        + "  transitiveQuoteIncludeDirectories="
        + transitiveQuoteIncludeDirectories
        + "\n"
        + "  transitiveDefines="
        + transitiveDefines
        + "\n"
        + "  transitiveSystemIncludeDirectories="
        + transitiveSystemIncludeDirectories
        + "\n"
        + '}';
  }
}
