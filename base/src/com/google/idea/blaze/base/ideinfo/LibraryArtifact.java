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
import java.io.Serializable;
import javax.annotation.Nullable;

/** Represents a jar artifact. */
public class LibraryArtifact implements Serializable {
  private static final long serialVersionUID = 2L;

  @Nullable public final ArtifactLocation interfaceJar;
  @Nullable public final ArtifactLocation classJar;
  @Nullable public final ArtifactLocation sourceJar;

  public LibraryArtifact(
      @Nullable ArtifactLocation interfaceJar,
      @Nullable ArtifactLocation classJar,
      @Nullable ArtifactLocation sourceJar) {
    if (interfaceJar == null && classJar == null) {
      throw new IllegalArgumentException("Interface and class jars cannot both be null.");
    }

    this.interfaceJar = interfaceJar;
    this.classJar = classJar;
    this.sourceJar = sourceJar;
  }

  /**
   * Returns the best jar to add to IntelliJ.
   *
   * <p>We prefer the interface jar if one exists, otherwise the class jar.
   */
  public ArtifactLocation jarForIntellijLibrary() {
    if (interfaceJar != null) {
      return interfaceJar;
    }
    return classJar;
  }

  @Override
  public String toString() {
    return String.format("jar=%s, ijar=%s, srcjar=%s", classJar, interfaceJar, sourceJar);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LibraryArtifact that = (LibraryArtifact) o;
    return Objects.equal(interfaceJar, that.interfaceJar)
        && Objects.equal(classJar, that.classJar)
        && Objects.equal(sourceJar, that.sourceJar);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(interfaceJar, classJar, sourceJar);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for library artifacts */
  public static class Builder {
    private ArtifactLocation interfaceJar;
    private ArtifactLocation classJar;
    private ArtifactLocation sourceJar;

    public Builder setInterfaceJar(ArtifactLocation artifactLocation) {
      this.interfaceJar = artifactLocation;
      return this;
    }

    public Builder setClassJar(@Nullable ArtifactLocation artifactLocation) {
      this.classJar = artifactLocation;
      return this;
    }

    public Builder setSourceJar(@Nullable ArtifactLocation artifactLocation) {
      this.sourceJar = artifactLocation;
      return this;
    }

    public LibraryArtifact build() {
      return new LibraryArtifact(interfaceJar, classJar, sourceJar);
    }
  }
}
