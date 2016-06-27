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
package com.google.idea.blaze.java.sync.source;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;

/**
 * Pairing of rule and source artifact.
 */
public class SourceArtifact {
  public final Label originatingRule;
  public final ArtifactLocation artifactLocation;

  public SourceArtifact(Label originatingRule, ArtifactLocation artifactLocation) {
    this.originatingRule = originatingRule;
    this.artifactLocation = artifactLocation;
  }

  public static Builder builder(Label originatingRule) {
    return new Builder(originatingRule);
  }

  public static class Builder {
    Label originatingRule;
    ArtifactLocation artifactLocation;

    Builder(Label originatingRule) {
      this.originatingRule = originatingRule;
    }

    public Builder setArtifactLocation(ArtifactLocation artifactLocation) {
      this.artifactLocation = artifactLocation;
      return this;
    }

    public Builder setArtifactLocation(ArtifactLocation.Builder artifactLocation) {
      return setArtifactLocation(artifactLocation.build());
    }

    public SourceArtifact build() {
      return new SourceArtifact(originatingRule, artifactLocation);
    }
  }
}
