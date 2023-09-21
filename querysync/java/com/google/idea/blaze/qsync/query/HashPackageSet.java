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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates a set of build packages, and includes utilities to find the containing or parent
 * package of a file or package.
 */
public class HashPackageSet implements PackageSet {

  private final ImmutableSet<Path> packages;

  public HashPackageSet(Set<Path> packages) {
    this.packages = ImmutableSet.copyOf(packages);
  }

  public static HashPackageSet of(Path... packages) {
    return new HashPackageSet(ImmutableSet.copyOf(packages));
  }

  public static HashPackageSet create(ImmutableSet<Path> packages) {
    return new HashPackageSet(packages);
  }

  @Override
  public boolean contains(Path packagePath) {
    return packages.contains(packagePath);
  }

  @Override
  public boolean isEmpty() {
    return packages.isEmpty();
  }

  @Override
  public int size() {
    return packages.size();
  }

  @VisibleForTesting
  public ImmutableSet<Path> asPathSet() {
    return packages;
  }

  /** Create a derived package set with the given packages removed from it. */
  public HashPackageSet deletePackages(HashPackageSet deletedPackages) {
    return new HashPackageSet(Sets.difference(packages, deletedPackages.packages));
  }

  /** Create a derived package set with the given packages added to it. */
  public HashPackageSet addPackages(HashPackageSet addedPackages) {
    return new HashPackageSet(Sets.union(packages, addedPackages.packages));
  }

  @Override
  public Optional<Path> getParentPackage(Path buildPackage) {
    return findIncludingPackage(buildPackage.getParent());
  }

  @Override
  public Optional<Path> findIncludingPackage(Path path) {
    while (path != null) {
      if (packages.contains(path)) {
        return Optional.of(path);
      }
      path = path.getParent();
    }
    return Optional.empty();
  }

  @Override
  public PackageSet getSubpackages(Path root) {
    return new HashPackageSet(
        packages.stream().filter(p -> p.startsWith(root)).collect(toImmutableSet()));
  }

  /** Builder for {@link PackageSet}. */
  public static class Builder {
    private final ImmutableSet.Builder<Path> set;

    public Builder() {
      set = ImmutableSet.builder();
    }

    @CanIgnoreReturnValue
    public Builder add(Path p) {
      set.add(p);
      return this;
    }

    public HashPackageSet build() {
      return new HashPackageSet(set.build());
    }
  }
}
