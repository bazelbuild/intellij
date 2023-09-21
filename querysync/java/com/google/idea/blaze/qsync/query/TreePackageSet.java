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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates a set of build packages, and includes utilities to find the containing or parent
 * package of a file or package.
 */
public class TreePackageSet implements PackageSet {

  private final PackageTree<Boolean> packages;

  private TreePackageSet(PackageTree<Boolean> packages) {
    this.packages = packages;
  }

  public TreePackageSet(Set<Path> packages) {
    PackageTree.Builder<Boolean> builder = new PackageTree.Builder<Boolean>();
    for (Path pkg : packages) {
      builder.add(pkg, true);
    }
    this.packages = builder.build();
  }

  public static TreePackageSet of(Path... packages) {
    return new TreePackageSet(ImmutableSet.copyOf(packages));
  }

  @Override
  public boolean contains(Path packagePath) {
    return packages.get(packagePath).contains(true);
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
  @Override
  public ImmutableSet<Path> asPathSet() {
    return packages.asPathSet();
  }

  @Override
  public Optional<Path> getParentPackage(Path buildPackage) {
    return findIncludingPackage(buildPackage.getParent());
  }

  @Override
  public Optional<Path> findIncludingPackage(Path path) {
    return packages.findIncludingPackage(path);
  }

  @Override
  public TreePackageSet getSubpackages(Path root) {
    return new TreePackageSet(packages.getSubpackages(root));
  }

  /** Builder for {@link TreePackageSet}. */
  public static class Builder {
    private final PackageTree.Builder<Boolean> builder = new PackageTree.Builder<Boolean>();

    public Builder() {}

    public TreePackageSet build() {
      return new TreePackageSet(builder.build());
    }

    @CanIgnoreReturnValue
    public Builder add(Path newPackage) {
      builder.add(newPackage, true);
      return this;
    }
  }
}
