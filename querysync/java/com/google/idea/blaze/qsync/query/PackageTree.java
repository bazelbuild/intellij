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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Encapsulates a set of build packages, and includes utilities to find the containing or parent
 * package of a file or package.
 */
public class PackageTree<T> {

  public static final PackageTree EMPTY = new PackageTree(Node.EMPTY);

  private final Node<T> root;

  private PackageTree(Node<T> root) {
    this.root = root;
  }

  public ImmutableSet<T> get(Path packagePath) {
    return root.get(packagePath);
  }

  public boolean isEmpty() {
    return root.children().isEmpty() && root.content().isEmpty();
  }

  public int size() {
    return root.size();
  }

  @VisibleForTesting
  public ImmutableSet<Path> asPathSet() {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
    root.toPathSet(builder, Path.of(""));
    return builder.build();
  }

  @VisibleForTesting
  public ImmutableMultimap<Path, T> asMap() {
    ImmutableMultimap.Builder<Path, T> builder = ImmutableMultimap.builder();
    root.toMap(builder, Path.of(""));
    return builder.build();
  }

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  public Optional<Path> getParentPackage(Path buildPackage) {
    return findIncludingPackage(buildPackage.getParent());
  }

  /**
   * Find the package from within this set that is the best match of the given path.
   *
   * <p>If {@code path} is a package in this set, return it. If {@code path} has a parent in this
   * set, return the closest such parent. Otherwise, returns empty.
   */
  public Optional<Path> findIncludingPackage(Path path) {
    return root.findIncludingPackage(path);
  }

  public PackageTree<T> getSubpackages(Path root) {
    return new PackageTree<T>(this.root.getSubpackages(root, Path.of("")));
  }

  private interface NodeOrBuilder {
    public Builder buildUpon();

    public Node buildNode();
  }

  @AutoValue
  abstract static class Node<T> implements NodeOrBuilder {

    static final Node<?> EMPTY =
        new AutoValue_PackageTree_Node(ImmutableSet.of(), ImmutableMap.of());

    abstract ImmutableSet<T> content();

    abstract ImmutableMap<Path, Node<T>> children();

    static <T> Node<T> create(ImmutableMap<Path, Node<T>> children, ImmutableSet<T> content) {
      return new AutoValue_PackageTree_Node<T>(content, children);
    }

    @Memoized
    int size() {
      return (content().size()) + children().values().stream().mapToInt(Node::size).sum();
    }

    @Override
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public Node buildNode() {
      return this;
    }

    ImmutableSet<T> get(Path pkg) {
      if (pkg.toString().isEmpty()) {
        return content();
      }
      return children().entrySet().stream()
          .filter(e -> pkg.startsWith(e.getKey()))
          .findFirst()
          .map(e -> e.getValue().get(e.getKey().relativize(pkg)))
          .orElse(ImmutableSet.of());
    }

    void toPathSet(ImmutableSet.Builder<Path> builder, Path myRoot) {
      if (!content().isEmpty()) {
        builder.add(myRoot);
      }
      for (Map.Entry<Path, Node<T>> e : children().entrySet()) {
        e.getValue().toPathSet(builder, myRoot.resolve(e.getKey()));
      }
    }

    void toMap(ImmutableMultimap.Builder<Path, T> builder, Path myRoot) {
      builder.putAll(myRoot, content());
      for (Map.Entry<Path, Node<T>> e : children().entrySet()) {
        e.getValue().toMap(builder, myRoot.resolve(e.getKey()));
      }
    }

    Optional<Path> findIncludingPackage(Path pkg) {
      Path existingChild = null;
      Path commonPrefix = null;
      for (Path childPath : children().keySet()) {
        commonPrefix = commonPrefix(childPath, pkg);
        if (commonPrefix != null) {
          existingChild = childPath;
          break;
        }
      }

      if (commonPrefix != null && commonPrefix.equals(existingChild)) {
        Optional<Path> childMatch =
            children()
                .get(existingChild)
                .findIncludingPackage(existingChild.relativize(pkg))
                .map(existingChild::resolve);
        if (childMatch.isPresent()) {
          return childMatch;
        }
      }
      if (!content().isEmpty()) {
        return Optional.of(Path.of(""));
      }
      return Optional.empty();
    }

    Node getSubpackages(Path pkg, Path myRoot) {
      if (pkg.toString().isEmpty()) {
        Node newRoot =
            Node.<T>create(
                ImmutableMap.<Path, Node<T>>builder().put(myRoot, this).build(), ImmutableSet.of());
        return newRoot;
      }

      Path existingChild = null;
      Path commonPrefix = null;
      for (Path childPath : children().keySet()) {
        commonPrefix = commonPrefix(childPath, pkg);
        if (commonPrefix != null) {
          existingChild = childPath;
          break;
        }
      }
      if (commonPrefix == null) {
        return Node.EMPTY;
      }
      if (commonPrefix.equals(existingChild)) {
        return children()
            .get(existingChild)
            .getSubpackages(existingChild.relativize(pkg), myRoot.resolve(existingChild));
      } else {
        return children()
            .get(existingChild)
            .getSubpackages(Path.of(""), myRoot.resolve(existingChild));
      }
    }
  }

  /** Builder for {@link PackageTree}. */
  public static class Builder<T> implements NodeOrBuilder {
    private ImmutableSet.Builder<T> content = ImmutableSet.builder();
    private final Map<Path, NodeOrBuilder> children = new HashMap<>();

    public Builder() {
      this(ImmutableSet.of());
    }

    public Builder(Node<T> buildUpon) {
      content.addAll(buildUpon.content());
      children.putAll(buildUpon.children());
    }

    Builder(ImmutableSet<T> content) {
      this.content.addAll(content);
    }

    @Override
    public Builder buildUpon() {
      return this;
    }

    public PackageTree<T> build() {
      return new PackageTree(buildNode());
    }

    @Override
    public Node<T> buildNode() {
      ImmutableMap.Builder<Path, Node<T>> builder = ImmutableMap.builder();
      for (Map.Entry<Path, NodeOrBuilder> e : children.entrySet()) {
        builder.put(e.getKey(), e.getValue().buildNode());
      }
      return Node.create(builder.build(), content.build());
    }

    @CanIgnoreReturnValue
    public NodeOrBuilder add(Path newPackage, T value) {
      if (newPackage.toString().isEmpty()) {
        content.add(value);
        return this;
      }
      Path existingChild = null;
      Path commonPrefix = null;
      for (Path childPath : children.keySet()) {
        commonPrefix = commonPrefix(childPath, newPackage);
        if (commonPrefix != null) {
          existingChild = childPath;
          break;
        }
      }
      if (commonPrefix == null) {
        children.put(newPackage, new Builder(ImmutableSet.of(value)));
        return this;
      }

      if (!existingChild.equals(commonPrefix)) {
        // we need to split a child to add this new node.
        // e.g. we have "a/b/c" and were adding "a/b/d"
        Builder newNode = new Builder(ImmutableSet.of());
        newNode.children.put(
            commonPrefix.relativize(existingChild), children.remove(existingChild));
        children.put(commonPrefix, newNode);
      }

      children.replace(
          commonPrefix,
          children.get(commonPrefix).buildUpon().add(commonPrefix.relativize(newPackage), value));
      return this;
    }
  }

  @Nullable
  static Path commonPrefix(Path a, Path b) {
    int i = 0;
    while (a.getNameCount() > i && b.getNameCount() > i && a.getName(i).equals(b.getName(i))) {
      i++;
    }
    if (i == 0) {
      return null;
    }
    return a.subpath(0, i);
  }
}
