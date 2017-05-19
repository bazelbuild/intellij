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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;
import java.util.Collection;
import javax.annotation.Nullable;

/** Ide info specific to android rules. */
public final class AndroidIdeInfo implements Serializable {
  private static final long serialVersionUID = 5L;

  public final Collection<ArtifactLocation> resources;
  @Nullable public final ArtifactLocation manifest;
  @Nullable public final LibraryArtifact idlJar;
  @Nullable public final LibraryArtifact resourceJar;
  public final boolean hasIdlSources;
  @Nullable public final String resourceJavaPackage;
  public boolean generateResourceClass;
  @Nullable public Label legacyResources;

  public AndroidIdeInfo(
      Collection<ArtifactLocation> resources,
      @Nullable String resourceJavaPackage,
      boolean generateResourceClass,
      @Nullable ArtifactLocation manifest,
      @Nullable LibraryArtifact idlJar,
      @Nullable LibraryArtifact resourceJar,
      boolean hasIdlSources,
      @Nullable Label legacyResources) {
    this.resources = resources;
    this.resourceJavaPackage = resourceJavaPackage;
    this.generateResourceClass = generateResourceClass;
    this.manifest = manifest;
    this.idlJar = idlJar;
    this.resourceJar = resourceJar;
    this.hasIdlSources = hasIdlSources;
    this.legacyResources = legacyResources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for android rule */
  public static class Builder {
    private Collection<ArtifactLocation> resources = Lists.newArrayList();
    private ArtifactLocation manifest;
    private LibraryArtifact idlJar;
    private LibraryArtifact resourceJar;
    private boolean hasIdlSources;
    private String resourceJavaPackage;
    private boolean generateResourceClass;
    private Label legacyResources;

    public Builder setManifestFile(ArtifactLocation artifactLocation) {
      this.manifest = artifactLocation;
      return this;
    }

    public Builder addResource(ArtifactLocation artifactLocation) {
      this.resources.add(artifactLocation);
      return this;
    }

    public Builder setIdlJar(LibraryArtifact idlJar) {
      this.idlJar = idlJar;
      return this;
    }

    public Builder setHasIdlSources(boolean hasIdlSources) {
      this.hasIdlSources = hasIdlSources;
      return this;
    }

    public Builder setResourceJar(LibraryArtifact.Builder resourceJar) {
      this.resourceJar = resourceJar.build();
      return this;
    }

    public Builder setResourceJavaPackage(@Nullable String resourceJavaPackage) {
      this.resourceJavaPackage = resourceJavaPackage;
      return this;
    }

    public Builder setGenerateResourceClass(boolean generateResourceClass) {
      this.generateResourceClass = generateResourceClass;
      return this;
    }

    public Builder setLegacyResources(@Nullable Label legacyResources) {
      this.legacyResources = legacyResources;
      return this;
    }

    public AndroidIdeInfo build() {
      if (!resources.isEmpty() || manifest != null) {
        if (!generateResourceClass) {
          throw new IllegalStateException(
              "Must set generateResourceClass if manifest or resources set");
        }
      }

      return new AndroidIdeInfo(
          resources,
          resourceJavaPackage,
          generateResourceClass,
          manifest,
          idlJar,
          resourceJar,
          hasIdlSources,
          legacyResources);
    }
  }
}
