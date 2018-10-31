/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala.sync.model;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public class BlazeScalaImportResult implements ProtoWrapper<ProjectData.BlazeJavaImportResult> {
  public final ImmutableMap<LibraryKey, BlazeJarLibrary> libraries;

  public BlazeScalaImportResult(ImmutableMap<LibraryKey, BlazeJarLibrary> libraries) {
    this.libraries = libraries;
  }

  static BlazeScalaImportResult fromProto(ProjectData.BlazeJavaImportResult proto) {
    return new BlazeScalaImportResult(
        ProtoWrapper.map(
            proto.getLibrariesMap(), LibraryKey::fromProto, BlazeJarLibrary::fromProto));
  }

  /**
   * Reusing {@link ProjectData.BlazeJavaImportResult} since {@link BlazeScalaImportResult} is a
   * subset of {@link BlazeJavaImportResult}.
   */
  @Override
  public ProjectData.BlazeJavaImportResult toProto() {
    return ProjectData.BlazeJavaImportResult.newBuilder()
        .putAllLibraries(ProtoWrapper.mapToProtos(libraries))
        .build();
  }
}
