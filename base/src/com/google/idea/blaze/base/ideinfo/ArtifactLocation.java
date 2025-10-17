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

  /**
   * The root beneath which this file resides. Not equal to File.root.path, normalizes relative paths and therefore also
   * includes the bazel-bin prefix for the artifact.
   */
  public abstract String rootPath();

  /**
   * The path of this file relative to its root. This excludes the aforementioned root, i.e. configuration-specific
   * fragments of the path. This path is can be different to File.short_path.
   */
  public abstract String relativePath();

  /**
   * True if this is a source file, i.e. it is not generated.
   */
  public abstract boolean isSource();

  /**
   * Whether this artifact comes from an external repository.
   */
  public abstract boolean isExternal();

  @SuppressWarnings("NoInterning")
  public static ArtifactLocation fromProto(Common.ArtifactLocation proto) {
    return ProjectDataInterner.intern(
        builder()
            .setRootPath(proto.getRootPath().intern())
            .setRelativePath(proto.getRelativePath())
            .setIsSource(proto.getIsSource())
            .setIsExternal(proto.getIsExternal())
            .build()
    );
  }

  @Override
  public Common.ArtifactLocation toProto() {
    return Common.ArtifactLocation.newBuilder()
        .setRootPath(rootPath())
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
   * The execution path of this file, relative to the workspace's execution directory. It consists of two parts, an
   * optional first part called the root, and the second part which is the relativePath. The root may be empty, which it
   * usually is for non-generated files. For generated files it usually contains a configuration-specific path fragment
   * that encodes things like the target CPU architecture that was used while building said file. Use the relativePath
   * for the path under which the file is mapped if it's in the runfiles of a binary.
   */
  public String getExecutionRootRelativePath() {
    return Paths.get(rootPath(), relativePath()).toString();
  }

  public static Builder builder() {
    return new AutoValue_ArtifactLocation.Builder()
        .setIsExternal(false)
        .setIsSource(true)
        .setRootPath("");
  }

  /**
   * Builder for an artifact location
   */
  @AutoValue.Builder
  public static abstract class Builder {

    public abstract Builder setRelativePath(String value);

    public abstract Builder setRootPath(String value);

    public abstract Builder setIsSource(boolean value);

    public abstract Builder setIsExternal(boolean value);

    public abstract ArtifactLocation build();

    public static Builder copy(ArtifactLocation artifact) {
      return builder()
          .setRelativePath(artifact.relativePath())
          .setRootPath(artifact.rootPath())
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
        .compare(rootPath(), o.rootPath())
        .compare(relativePath(), o.relativePath())
        .compareFalseFirst(isSource(), o.isSource())
        .compareFalseFirst(isExternal(), o.isExternal())
        .result();
  }
}
