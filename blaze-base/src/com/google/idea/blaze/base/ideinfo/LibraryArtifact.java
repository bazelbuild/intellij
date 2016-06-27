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
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Represents a jar artifact.
 */
public class LibraryArtifact implements Serializable {
  private static final long serialVersionUID = 1L;

  public final ArtifactLocation jar;
  @Nullable public final ArtifactLocation runtimeJar;
  @Nullable public final ArtifactLocation sourceJar;

  public LibraryArtifact(ArtifactLocation jar, @Nullable ArtifactLocation runtimeJar, @Nullable ArtifactLocation sourceJar) {
    this.jar = jar;
    this.runtimeJar = runtimeJar;
    this.sourceJar = sourceJar;
  }

  @Override
  public String toString() {
    return String.format("jar=%s, ijar=%s, srcjar=%s", runtimeJar, jar, sourceJar);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibraryArtifact that = (LibraryArtifact)o;
    return
      Objects.equal(jar, that.jar) &&
      Objects.equal(runtimeJar, that.runtimeJar) &&
      Objects.equal(sourceJar, that.sourceJar);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(jar, runtimeJar, sourceJar);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ArtifactLocation jar;
    private ArtifactLocation runtimeJar;
    private ArtifactLocation sourceJar;

    public Builder setJar(ArtifactLocation artifactLocation) {
      this.jar = artifactLocation;
      return this;
    }
    public Builder setRuntimeJar(@Nullable ArtifactLocation artifactLocation) {
      this.runtimeJar = artifactLocation;
      return this;
    }
    public Builder setSourceJar(@Nullable ArtifactLocation artifactLocation) {
      this.sourceJar = artifactLocation;
      return this;
    }
    public LibraryArtifact build() {
      return new LibraryArtifact(jar, runtimeJar, sourceJar);
    }
  }
}
