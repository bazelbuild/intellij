/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;

/**
 * Utility class to intern frequently duplicated objects in the project data.
 *
 * <p>The underlying interners are application-wide, not specific to a project.
 */
public final class ProjectDataInterner {
  private static final Interner<Label> labelInterner = Interners.newWeakInterner();
  private static final Interner<String> stringInterner = Interners.newWeakInterner();
  private static final Interner<TargetKey> targetKeyInterner = Interners.newWeakInterner();
  private static final Interner<Dependency> dependencyInterner = Interners.newWeakInterner();
  private static final Interner<ArtifactLocation> artifactLocationInterner =
      Interners.newWeakInterner();
  private static final Interner<ExecutionRootPath> executionRootPathInterner =
      Interners.newWeakInterner();
  private static final Interner<LibraryArtifact> libraryArtifactInterner =
      Interners.newWeakInterner();

  public static Label intern(Label label) {
    return labelInterner.intern(label);
  }

  static String intern(String string) {
    return stringInterner.intern(string);
  }

  static TargetKey intern(TargetKey targetKey) {
    return targetKeyInterner.intern(targetKey);
  }

  static Dependency intern(Dependency dependency) {
    return dependencyInterner.intern(dependency);
  }

  static ArtifactLocation intern(ArtifactLocation artifactLocation) {
    return artifactLocationInterner.intern(artifactLocation);
  }

  public static ExecutionRootPath intern(ExecutionRootPath executionRootPath) {
    return executionRootPathInterner.intern(executionRootPath);
  }

  static LibraryArtifact intern(LibraryArtifact libraryArtifact) {
    return libraryArtifactInterner.intern(libraryArtifact);
  }
}
