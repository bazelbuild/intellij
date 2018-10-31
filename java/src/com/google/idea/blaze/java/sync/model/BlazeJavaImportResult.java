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
package com.google.idea.blaze.java.sync.model;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import javax.annotation.Nullable;

/** The result of a blaze import operation. */
public class BlazeJavaImportResult implements ProtoWrapper<ProjectData.BlazeJavaImportResult> {
  public final ImmutableList<BlazeContentEntry> contentEntries;
  public final ImmutableMap<LibraryKey, BlazeJarLibrary> libraries;
  public final ImmutableList<ArtifactLocation> buildOutputJars;
  public final ImmutableSet<ArtifactLocation> javaSourceFiles;
  @Nullable public final String sourceVersion;

  public BlazeJavaImportResult(
      ImmutableList<BlazeContentEntry> contentEntries,
      ImmutableMap<LibraryKey, BlazeJarLibrary> libraries,
      ImmutableList<ArtifactLocation> buildOutputJars,
      ImmutableSet<ArtifactLocation> javaSourceFiles,
      @Nullable String sourceVersion) {
    this.contentEntries = contentEntries;
    this.libraries = libraries;
    this.buildOutputJars = buildOutputJars;
    this.javaSourceFiles = javaSourceFiles;
    this.sourceVersion = sourceVersion;
  }

  public static BlazeJavaImportResult fromProto(ProjectData.BlazeJavaImportResult proto) {
    return new BlazeJavaImportResult(
        ProtoWrapper.map(proto.getContentEntriesList(), BlazeContentEntry::fromProto),
        ProtoWrapper.map(
            proto.getLibrariesMap(), LibraryKey::fromProto, BlazeJarLibrary::fromProto),
        ProtoWrapper.map(proto.getBuildOutputJarsList(), ArtifactLocation::fromProto),
        ProtoWrapper.map(
            proto.getJavaSourceFilesList(),
            ArtifactLocation::fromProto,
            ImmutableSet.toImmutableSet()),
        Strings.emptyToNull(proto.getSourceVersion()));
  }

  @Override
  public ProjectData.BlazeJavaImportResult toProto() {
    ProjectData.BlazeJavaImportResult.Builder builder =
        ProjectData.BlazeJavaImportResult.newBuilder()
            .addAllContentEntries(ProtoWrapper.mapToProtos(contentEntries))
            .putAllLibraries(ProtoWrapper.mapToProtos(libraries))
            .addAllBuildOutputJars(ProtoWrapper.mapToProtos(buildOutputJars))
            .addAllJavaSourceFiles(ProtoWrapper.mapToProtos(javaSourceFiles));
    ProtoWrapper.setIfNotNull(builder::setSourceVersion, sourceVersion);
    return builder.build();
  }
}
