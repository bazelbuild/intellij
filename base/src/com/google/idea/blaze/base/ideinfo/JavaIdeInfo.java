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

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import javax.annotation.Nullable;

/** Ide info specific to java rules. */
public final class JavaIdeInfo implements Serializable {
  private static final long serialVersionUID = 2L;

  /**
   * The main jar(s) produced by this java rule.
   *
   * <p>Usually this will be a single jar, but java_imports support importing multiple jars.
   */
  public final Collection<LibraryArtifact> jars;

  /** A jar containing annotation processing. */
  public final Collection<LibraryArtifact> generatedJars;

  /**
   * A jar containing code from *only* generated sources, iff the rule contains both generated and
   * non-generated sources.
   */
  @Nullable public final LibraryArtifact filteredGenJar;

  /** File containing a map from .java files to their corresponding package. */
  @Nullable public final ArtifactLocation packageManifest;

  /** File containing dependencies. */
  @Nullable public final ArtifactLocation jdepsFile;

  /** main_class attribute value for java_binary targets */
  @Nullable public String javaBinaryMainClass;

  public JavaIdeInfo(
      Collection<LibraryArtifact> jars,
      Collection<LibraryArtifact> generatedJars,
      @Nullable LibraryArtifact filteredGenJar,
      @Nullable ArtifactLocation packageManifest,
      @Nullable ArtifactLocation jdepsFile,
      @Nullable String javaBinaryMainClass) {
    this.jars = jars;
    this.generatedJars = generatedJars;
    this.packageManifest = packageManifest;
    this.jdepsFile = jdepsFile;
    this.filteredGenJar = filteredGenJar;
    this.javaBinaryMainClass = javaBinaryMainClass;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for java info */
  public static class Builder {
    ImmutableList.Builder<LibraryArtifact> jars = ImmutableList.builder();
    ImmutableList.Builder<LibraryArtifact> generatedJars = ImmutableList.builder();
    @Nullable LibraryArtifact filteredGenJar;
    @Nullable String mainClass;

    public Builder addJar(LibraryArtifact.Builder jar) {
      jars.add(jar.build());
      return this;
    }

    public Builder addGeneratedJar(LibraryArtifact.Builder jar) {
      generatedJars.add(jar.build());
      return this;
    }

    public Builder setFilteredGenJar(LibraryArtifact.Builder jar) {
      this.filteredGenJar = jar.build();
      return this;
    }

    public Builder setMainClass(@Nullable String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    public JavaIdeInfo build() {
      return new JavaIdeInfo(
          jars.build(), generatedJars.build(), filteredGenJar, null, null, mainClass);
    }
  }
}
