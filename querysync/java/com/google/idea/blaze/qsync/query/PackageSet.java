/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Encapsulates a set of build packages, and includes utilities to find the containing or parent
 * package of a file or package.
 */
public interface PackageSet {

  PackageSet EMPTY = new HashPackageSet(ImmutableSet.of());

  boolean contains(Path packagePath);

  boolean isEmpty();

  int size();

  ImmutableSet<Path> asPathSet();

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  Optional<Path> getParentPackage(Path buildPackage);

  /**
   * Find the package from within this set that is the best match of the given path.
   *
   * <p>If {@code path} is a package in this set, return it. If {@code path} has a parent in this
   * set, return the closest such parent. Otherwise, returns empty.
   */
  Optional<Path> findIncludingPackage(Path path);

  PackageSet getSubpackages(Path root);
}
