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

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public class BlazeAndroidImportResult
    implements ProtoWrapper<ProjectData.BlazeAndroidImportResult> {
  public final ImmutableList<AndroidResourceModule> androidResourceModules;
  @Nullable public final BlazeResourceLibrary resourceLibrary;
  @Nullable public final ArtifactLocation javacJar;
  public final ImmutableList<AarLibrary> aarLibraries;

  public BlazeAndroidImportResult(
      ImmutableList<AndroidResourceModule> androidResourceModules,
      @Nullable BlazeResourceLibrary resourceLibrary,
      ImmutableList<AarLibrary> aarLibraries,
      @Nullable ArtifactLocation javacJar) {
    this.androidResourceModules = androidResourceModules;
    this.resourceLibrary = resourceLibrary;
    this.aarLibraries = aarLibraries;
    this.javacJar = javacJar;
  }

  static BlazeAndroidImportResult fromProto(ProjectData.BlazeAndroidImportResult proto) {
    return new BlazeAndroidImportResult(
        ProtoWrapper.map(proto.getAndroidResourceModulesList(), AndroidResourceModule::fromProto),
        !proto.getResourceLibrariesList().isEmpty()
            ? BlazeResourceLibrary.fromProto(proto.getResourceLibraries(0))
            : null,
        ProtoWrapper.map(proto.getAarLibrariesList(), AarLibrary::fromProto),
        proto.hasJavacJar() ? ArtifactLocation.fromProto(proto.getJavacJar()) : null);
  }

  @Override
  public ProjectData.BlazeAndroidImportResult toProto() {
    ProjectData.BlazeAndroidImportResult.Builder builder =
        ProjectData.BlazeAndroidImportResult.newBuilder()
            .addAllAndroidResourceModules(ProtoWrapper.mapToProtos(androidResourceModules))
            .addAllAarLibraries(ProtoWrapper.mapToProtos(aarLibraries));
    ProtoWrapper.unwrapAndSetIfNotNull(builder::addResourceLibraries, resourceLibrary);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavacJar, javacJar);
    return builder.build();
  }
}
