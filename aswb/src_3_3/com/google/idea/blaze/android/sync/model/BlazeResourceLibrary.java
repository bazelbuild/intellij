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
package com.google.idea.blaze.android.sync.model;

import static com.google.idea.blaze.android.sync.importer.BlazeImportInput.createLooksLikeAarLibrary;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import java.util.Set;
import javax.annotation.concurrent.Immutable;

/**
 * A library that contains information of resource directory and manifest file. It will be stored as
 * a looks like aar library
 */
@Immutable
public final class BlazeResourceLibrary extends BlazeLibrary {
  private static final long serialVersionUID = 5L;

  public final ArtifactLocation root;
  public final ArtifactLocation manifest;
  public final ImmutableSet<String> resources;

  private BlazeResourceLibrary(
      ArtifactLocation root, ImmutableSet<String> resources, ArtifactLocation manifest) {
    super(new LibraryKey(libraryNameFromArtifactLocation(root)));
    this.root = root;
    this.manifest = manifest;
    this.resources = resources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for an blaze resource library */
  public static class Builder {
    ArtifactLocation root;
    ArtifactLocation manifest;
    ImmutableSet.Builder<String> resources = ImmutableSet.builder();

    public BlazeResourceLibrary.Builder setRoot(ArtifactLocation root) {
      this.root = root;
      return this;
    }

    public BlazeResourceLibrary.Builder setManifest(ArtifactLocation manifest) {
      this.manifest = manifest;
      return this;
    }

    public BlazeResourceLibrary.Builder addResource(String resource) {
      resources.add(resource);
      return this;
    }

    public BlazeResourceLibrary.Builder addResources(Set<String> resources) {
      this.resources.addAll(resources);
      return this;
    }

    public BlazeResourceLibrary build() {
      return new BlazeResourceLibrary(root, resources.build(), manifest);
    }
  }

  public static String libraryNameFromArtifactLocation(ArtifactLocation resource) {
    // resource would be in format <some path>/res, so LibraryKey.libraryNameFromArtifactLocation
    // will return res_<hash of parent path> which is hard to identify when we have hundreds of
    // resource libraries. Adding relative path as prefix for easy distinction.
    int index = resource.getRelativePath().lastIndexOf("/");
    String prefix = "";
    if (index > 0) {
      prefix =
          FileUtil.sanitizeFileName(resource.getRelativePath().substring(0, index), true, "_")
              + "_";
    }
    return prefix + LibraryKey.libraryNameFromArtifactLocation(resource);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), root, manifest, resources);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeResourceLibrary)) {
      return false;
    }

    BlazeResourceLibrary that = (BlazeResourceLibrary) other;

    return super.equals(other)
        && Objects.equal(root, that.root)
        && Objects.equal(manifest, that.manifest)
        && Objects.equal(resources, that.resources);
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      Library.ModifiableModel libraryModel) {
    // TODO: Check if we need update library entry after converting to use project model to store
    // resource library information.

    // OrderRootType.SOURCES is used for source roots for libraries not resource
    // In an aar library, path to resource directory should be tag with OrderRootType.CLASSES
    libraryModel.addRoot(
        pathToUrl(artifactLocationDecoder.decode(root)),
        createLooksLikeAarLibrary.getValue() ? OrderRootType.CLASSES : OrderRootType.SOURCES);
  }
}
