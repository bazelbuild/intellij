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
import com.google.common.collect.ComparisonChain;
import com.google.devtools.intellij.aspect.Common;
import java.nio.file.Paths;

/**
 * Represents a blaze-produced artifact.
 */
@AutoValue
public abstract class ArtifactLocation implements ProtoWrapper<Common.ArtifactLocation>, Comparable<ArtifactLocation> {

  public abstract String rootExecutionPathFragment();

  /**
   * The root-relative path. For external workspace artifacts, this is relative to the external workspace root.
   */
  public abstract String relativePath();

  public abstract boolean isSource();

  public abstract boolean isExternal();


  @SuppressWarnings("NoInterning")
  public static ArtifactLocation fromProto(Common.ArtifactLocation proto) {
    return ProjectDataInterner.intern(
        new AutoValue_ArtifactLocation(
            proto.getRootExecutionPathFragment().intern(),
            proto.getRelativePath(),
            proto.getIsSource(),
            proto.getIsExternal()
        )
    );
  }

  @Override
  public Common.ArtifactLocation toProto() {
    return Common.ArtifactLocation.newBuilder()
        .setRootExecutionPathFragment(rootExecutionPathFragment())
        .setRelativePath(relativePath())
        .setIsSource(isSource())
        .setIsExternal(isExternal())
        .build();
  }

  public boolean isGenerated() {
    return !isSource();
  }

  /**
   * Returns false for generated or external artifacts
   */
  public boolean isMainWorkspaceSourceArtifact() {
    return isSource() && !isExternal();
  }

  /**
   * For main-workspace source artifacts, this is simply the workspace-relative path.
   */
  public String getExecutionRootRelativePath() {
    return Paths.get(rootExecutionPathFragment(), relativePath()).toString();
  }

  public static Builder builder() {
    return new AutoValue_ArtifactLocation.Builder();
  }

  /**
   * Builder for an artifact location
   */
  @AutoValue.Builder
  public abstract class Builder {

    public abstract Builder setRelativePath(String value);

    public abstract Builder setRootExecutionPathFragment(String value);

    public abstract Builder setIsSource(boolean value);

    public abstract Builder setIsExternal(boolean isExternal);

    public abstract ArtifactLocation build();

    public static Builder copy(ArtifactLocation artifact) {
      return builder()
          .setRelativePath(artifact.relativePath())
          .setRootExecutionPathFragment(artifact.rootExecutionPathFragment())
          .setIsSource(artifact.isSource())
          .setIsExternal(artifact.isExternal());
    }
  }

  @Override
  public String toString() {
    return getExecutionRootRelativePath();
  }

  @Override
  public int compareTo(ArtifactLocation o) {
    return ComparisonChain.start()
        .compare(rootExecutionPathFragment(), o.rootExecutionPathFragment())
        .compare(relativePath(), o.relativePath())
        .compareFalseFirst(isSource(), o.isSource())
        .compareFalseFirst(isExternal(), o.isExternal())
        .result();
  }
}
