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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public final class BlazeAndroidImportResult
    implements ProtoWrapper<ProjectData.BlazeAndroidImportResult> {
  public final ImmutableList<AndroidResourceModule> androidResourceModules;
  // map from library key to AarLibrary.
  // Key is generated according to ArtifactLocation of aar file location
  public final ImmutableMap<String, AarLibrary> aarLibraries;
  public final ImmutableList<ArtifactLocation> javacJars;

  public BlazeAndroidImportResult(
      ImmutableList<AndroidResourceModule> androidResourceModules,
      ImmutableMap<String, AarLibrary> aarLibraries,
      ImmutableList<ArtifactLocation> javacJars) {
    this.androidResourceModules = androidResourceModules;
    this.aarLibraries = aarLibraries;
    this.javacJars = javacJars;
  }

  static BlazeAndroidImportResult fromProto(ProjectData.BlazeAndroidImportResult proto) {
    ImmutableList<ArtifactLocation> javacJars;
    if (proto.getJavacJarsCount() > 0) {
      javacJars =
          proto.getJavacJarsList().stream()
              .map(ArtifactLocation::fromProto)
              .collect(toImmutableList());
    } else {
      javacJars =
          proto.hasJavacJar()
              ? ImmutableList.of(ArtifactLocation.fromProto(proto.getJavacJar()))
              : ImmutableList.of();
    }
    return new BlazeAndroidImportResult(
        ProtoWrapper.map(proto.getAndroidResourceModulesList(), AndroidResourceModule::fromProto),
        proto.getAarLibrariesList().stream()
            .map(AarLibrary::fromProto)
            .collect(
                ImmutableMap.toImmutableMap(
                    library -> LibraryKey.libraryNameFromArtifactLocation(library.aarArtifact),
                    Functions.identity())),
        javacJars);
  }

  @Override
  public ProjectData.BlazeAndroidImportResult toProto() {
    return ProjectData.BlazeAndroidImportResult.newBuilder()
        .addAllAndroidResourceModules(ProtoWrapper.mapToProtos(androidResourceModules))
        .addAllAarLibraries(ProtoWrapper.mapToProtos(aarLibraries.values()))
        .addAllJavacJars(ProtoWrapper.mapToProtos(javacJars))
        .build();
  }
}
