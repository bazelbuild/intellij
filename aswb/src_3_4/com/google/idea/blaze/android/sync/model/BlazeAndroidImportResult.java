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

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public final class BlazeAndroidImportResult
    implements ProtoWrapper<ProjectData.BlazeAndroidImportResult> {
  public final ImmutableList<AndroidResourceModule> androidResourceModules;
  // map from library key to BlazeResourceLibrary.
  // Key is generated according to ArtifactLocation of resource directory
  @Nullable public final ImmutableMap<String, BlazeResourceLibrary> resourceLibraries;
  // map from library key to AarLibrary.
  // Key is generated according to ArtifactLocation of aar file location
  @Nullable public final ImmutableMap<String, AarLibrary> aarLibraries;
  @Nullable public final ArtifactLocation javacJar;

  public BlazeAndroidImportResult(
      ImmutableList<AndroidResourceModule> androidResourceModules,
      ImmutableMap<String, BlazeResourceLibrary> resourceLibraries,
      ImmutableMap<String, AarLibrary> aarLibraries,
      @Nullable ArtifactLocation javacJar) {
    this.androidResourceModules = androidResourceModules;
    this.resourceLibraries = resourceLibraries;
    this.aarLibraries = aarLibraries;
    this.javacJar = javacJar;
  }

  static BlazeAndroidImportResult fromProto(ProjectData.BlazeAndroidImportResult proto) {
    return new BlazeAndroidImportResult(
        ProtoWrapper.map(proto.getAndroidResourceModulesList(), AndroidResourceModule::fromProto),
        proto.getResourceLibrariesList().stream()
            .map(BlazeResourceLibrary::fromProto)
            .collect(
                ImmutableMap.toImmutableMap(
                    library -> BlazeResourceLibrary.libraryNameFromArtifactLocation(library.root),
                    Functions.identity())),
        proto.getAarLibrariesList().stream()
            .map(AarLibrary::fromProto)
            .collect(
                ImmutableMap.toImmutableMap(
                    library -> LibraryKey.libraryNameFromArtifactLocation(library.aarArtifact),
                    Functions.identity())),
        proto.hasJavacJar() ? ArtifactLocation.fromProto(proto.getJavacJar()) : null);
  }

  @Override
  public ProjectData.BlazeAndroidImportResult toProto() {
    ProjectData.BlazeAndroidImportResult.Builder builder =
        ProjectData.BlazeAndroidImportResult.newBuilder()
            .addAllAndroidResourceModules(ProtoWrapper.mapToProtos(androidResourceModules))
            .addAllResourceLibraries(ProtoWrapper.mapToProtos(resourceLibraries.values()))
            .addAllAarLibraries(ProtoWrapper.mapToProtos(aarLibraries.values()));
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavacJar, javacJar);
    return builder.build();
  }
}
