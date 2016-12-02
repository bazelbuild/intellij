/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import javax.annotation.concurrent.Immutable;

/** A library that contains sources. */
@Immutable
public final class BlazeResourceLibrary extends BlazeLibrary {
  private static final long serialVersionUID = 2L;

  public final ImmutableList<ArtifactLocation> sources;

  public BlazeResourceLibrary(ImmutableList<ArtifactLocation> sources) {
    super(LibraryKey.forResourceLibrary());
    this.sources = sources;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), sources);
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

    return super.equals(other) && Objects.equal(sources, that.sources);
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      Library.ModifiableModel libraryModel) {
    for (ArtifactLocation file : sources) {
      libraryModel.addRoot(pathToUrl(artifactLocationDecoder.decode(file)), OrderRootType.SOURCES);
    }
  }
}
