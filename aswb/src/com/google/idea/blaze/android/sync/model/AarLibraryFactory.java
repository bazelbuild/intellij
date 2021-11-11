/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.common.experiments.BoolExperiment;
import org.jetbrains.annotations.Nullable;

/**
 * A factory to create {@link AarLibrary}. {@link AarLibrary} will be created with different {@link
 * LibraryKey}. If aars shard the same resource package name and merge aar library feature is
 * enabled, resource package name will be used as key. Otherwise, aar {@link ArtifactLocation} will
 * be used.
 */
public final class AarLibraryFactory {
  public static final BoolExperiment mergeAarLibraries =
      new BoolExperiment("aswb.aarlibrary.merge", true);

  @VisibleForTesting
  public static final BoolExperiment exportResourcePackage =
      new BoolExperiment("aswb.aarlibrary.export.res.package", true);

  public static AarLibrary create(ArtifactLocation aarArtifact, @Nullable String resourcePackage) {
    return create(null, aarArtifact, resourcePackage);
  }

  public static AarLibrary create(
      @Nullable LibraryArtifact libraryArtifact,
      ArtifactLocation aarArtifact,
      @Nullable String resourcePackage) {
    resourcePackage = getResourcePackage(resourcePackage);
    return new AarLibrary(
        createLibraryKey(resourcePackage, aarArtifact),
        libraryArtifact,
        aarArtifact,
        resourcePackage);
  }

  private static LibraryKey createLibraryKey(
      @Nullable String resourcePackage, ArtifactLocation aarArtifact) {
    if (mergeAarLibraries.getValue() && resourcePackage != null) {
      return LibraryKey.fromIntelliJLibraryName(resourcePackage);
    }
    return LibraryKey.fromArtifactLocation(aarArtifact);
  }

  private static String getResourcePackage(@Nullable String resourcePackage) {
    return exportResourcePackage.getValue() ? resourcePackage : null;
  }

  private AarLibraryFactory() {}
}
