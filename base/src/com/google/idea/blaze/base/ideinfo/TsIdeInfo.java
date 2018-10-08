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
import java.io.Serializable;

/** Ide info specific to typescript rules. */
public class TsIdeInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<ArtifactLocation> sources;

  public TsIdeInfo(ImmutableList<ArtifactLocation> sources) {
    this.sources = sources;
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for js rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public TsIdeInfo build() {
      return new TsIdeInfo(sources.build());
    }
  }

  @Override
  public String toString() {
    return "TsIdeInfo{" + "\n" + "  sources=" + getSources() + "\n" + '}';
  }
}
