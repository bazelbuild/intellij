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
import java.io.File;
import java.io.Serializable;
import java.nio.file.Paths;

/** Represents a blaze-produced artifact. */
public final class ArtifactLocation implements Serializable {
  private static final long serialVersionUID = 2L;

  public final String rootPath;
  public final String rootExecutionPathFragment;
  public final String relativePath;
  public final boolean isSource;

  private ArtifactLocation(
      String rootPath, String rootExecutionPathFragment, String relativePath, boolean isSource) {
    this.rootPath = rootPath;
    this.rootExecutionPathFragment = rootExecutionPathFragment;
    this.relativePath = relativePath;
    this.isSource = isSource;
  }

  /** Returns the root path of the artifact, eg. blaze-out */
  public String getRootPath() {
    return rootPath;
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

  public File getFile() {
    return new File(getRootPath(), getRelativePath());
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
    String rootPath;
    String relativePath;
    String rootExecutionPathFragment = "";
    boolean isSource;

    public Builder setRootPath(String rootPath) {
      this.rootPath = rootPath;
      return this;
    }

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
      return new ArtifactLocation(rootPath, rootExecutionPathFragment, relativePath, isSource);
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
    return Objects.equal(rootPath, that.rootPath)
        && Objects.equal(rootExecutionPathFragment, that.rootExecutionPathFragment)
        && Objects.equal(relativePath, that.relativePath)
        && Objects.equal(isSource, that.isSource);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rootPath, rootExecutionPathFragment, relativePath, isSource);
  }

  @Override
  public String toString() {
    return getFile().toString();
  }
}
