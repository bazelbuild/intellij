/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import java.util.Objects;

/** Generic Ide info. */
public final class GenericIdeInfo implements ProtoWrapper<IntellijIdeInfo.GenericIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;

  private GenericIdeInfo(ImmutableList<ArtifactLocation> sources) {
    this.sources = sources;
  }

  static GenericIdeInfo fromProto(IntellijIdeInfo.GenericIdeInfo proto) {
    return new GenericIdeInfo(ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto));
  }

  @Override
  public IntellijIdeInfo.GenericIdeInfo toProto() {
    return IntellijIdeInfo.GenericIdeInfo.newBuilder()
        .addAllSources(ProtoWrapper.mapToProtos(sources))
        .build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for proto rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public GenericIdeInfo build() {
      return new GenericIdeInfo(sources.build());
    }
  }

  @Override
  public String toString() {
    return "GenericIdeInfo{" + "\n" + "  sources=" + getSources() + "\n" + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenericIdeInfo that = (GenericIdeInfo) o;
    return Objects.equals(sources, that.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources);
  }
}
