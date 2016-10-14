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

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import java.io.Serializable;
import java.nio.file.Paths;

/** Represents a blaze-produced artifact. */
public final class ArtifactLocation implements Serializable, Comparable<ArtifactLocation> {
  private static final long serialVersionUID = 3L;

  public final String rootExecutionPathFragment;
  public final String relativePath;
  public final boolean isSource;

  private ArtifactLocation(
      String rootExecutionPathFragment, String relativePath, boolean isSource) {
    this.rootExecutionPathFragment = rootExecutionPathFragment;
    this.relativePath = relativePath;
    this.isSource = isSource;
  }

  /** Gets the path relative to the root path. */
  public String getRelativePath() {
    return relativePath;
  }

  public boolean isSource() {
    return isSource;
  }

  public boolean isGenerated() {
    return !isSource;
  }

  /**
   * Returns rootExecutionPathFragment + relativePath. For source artifacts, this is simply
   * relativePath
   */
  public String getExecutionRootRelativePath() {
    return Paths.get(rootExecutionPathFragment, relativePath).toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for an artifact location */
  public static class Builder {
    String relativePath;
    String rootExecutionPathFragment = "";
    boolean isSource;

    public Builder setRelativePath(String relativePath) {
      this.relativePath = relativePath;
      return this;
    }

    public Builder setRootExecutionPathFragment(String rootExecutionPathFragment) {
      this.rootExecutionPathFragment = rootExecutionPathFragment;
      return this;
    }

    public Builder setIsSource(boolean isSource) {
      this.isSource = isSource;
      return this;
    }

    public ArtifactLocation build() {
      return new ArtifactLocation(rootExecutionPathFragment, relativePath, isSource);
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
    ArtifactLocation that = (ArtifactLocation) o;
    return Objects.equal(rootExecutionPathFragment, that.rootExecutionPathFragment)
        && Objects.equal(relativePath, that.relativePath)
        && Objects.equal(isSource, that.isSource);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rootExecutionPathFragment, relativePath, isSource);
  }

  @Override
  public String toString() {
    return getExecutionRootRelativePath();
  }

  @Override
  public int compareTo(ArtifactLocation o) {
    return ComparisonChain.start()
        .compare(rootExecutionPathFragment, o.rootExecutionPathFragment)
        .compare(relativePath, o.relativePath)
        .compare(isSource, o.isSource)
        .result();
  }
}
