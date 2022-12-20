/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Represents the basic structure of an IntelliJ project. */
@AutoValue
public abstract class ProjectStructure {

  /**
   * Returns the java source roots.
   *
   * <p>The keys in the outer map are workspace-relative source roots, as specified in the project
   * view. The keys in the inner maps are java source roots with the projec specified source root.
   * The values are the corresponding java package prefixes.
   *
   * <p>TODO: Replace this map with a class based structure to make it easier to understand.
   */
  public abstract ImmutableMap<String, ImmutableMap<String, String>> javaSourceRoots();

  /** The set of android source java packages (used in generating R classes). */
  public abstract ImmutableSet<String> androidSourcePackages();

  /** The set of android resource directories. */
  public abstract ImmutableSet<String> androidResourceDirectories();

  public static ProjectStructure create(
      ImmutableMap<String, ImmutableMap<String, String>> javaSourceRoots,
      ImmutableSet<String> androidSourcePackage,
      ImmutableSet<String> androidResourceDirectories) {
    return new AutoValue_ProjectStructure(
        javaSourceRoots, androidSourcePackage, androidResourceDirectories);
  }
}
