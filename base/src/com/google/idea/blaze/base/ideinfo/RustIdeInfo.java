/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import javax.annotation.Nullable;
import java.util.Objects;

/** Ide info specific to Rust rules. */
public final class RustIdeInfo implements ProtoWrapper<IntellijIdeInfo.RustIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;

  private RustIdeInfo(ImmutableList<ArtifactLocation> sources) {
    this.sources = sources;
  }

  static RustIdeInfo fromProto(IntellijIdeInfo.RustIdeInfo proto) {
    return new RustIdeInfo(ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto));
  }

  @Override
  public IntellijIdeInfo.RustIdeInfo toProto() {
    IntellijIdeInfo.RustIdeInfo.Builder builder =
        IntellijIdeInfo.RustIdeInfo.newBuilder()
            .addAllSources(ProtoWrapper.mapToProtos(sources));
    return builder.build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for rust info */
  public static class Builder {

    public RustIdeInfo build() {
      return new RustIdeInfo(ImmutableList.of());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RustIdeInfo that = (RustIdeInfo) o;
    return Objects.equals(sources, that.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources);
  }
}
