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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.io.Serializable;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public class BlazeAndroidImportResult implements Serializable {
  private static final long serialVersionUID = 5473739920232433527L;
  public final ImmutableCollection<AndroidResourceModule> androidResourceModules;
  // map from library key to BlazeResourceLibrary.
  // Key is generated according to ArtifactLocation of resource directory
  @Nullable public final ImmutableMap<String, BlazeResourceLibrary> resourceLibraries;
  // map from library key to AarLibrary.
  // Key is generated according to ArtifactLocation of aar file location
  @Nullable public final ImmutableMap<String, AarLibrary> aarLibraries;
  @Nullable public final ArtifactLocation javacJar;

  public BlazeAndroidImportResult(
      ImmutableCollection<AndroidResourceModule> androidResourceModules,
      ImmutableMap<String, BlazeResourceLibrary> resourceLibraries,
      ImmutableMap<String, AarLibrary> aarLibraries,
      @Nullable ArtifactLocation javacJar) {
    this.androidResourceModules = androidResourceModules;
    this.resourceLibraries = resourceLibraries;
    this.aarLibraries = aarLibraries;
    this.javacJar = javacJar;
  }
}
