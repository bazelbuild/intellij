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
package com.google.idea.blaze.qsync.project;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Encapsulates a set of targets, represented as Labels.
 *
 * <p>This class uses a tree to store the set of targets so that finding all the child targets of a
 * given directory is fast.
 */
public class TargetTree {

  public static final TargetTree EMPTY = new TargetTree(Node.EMPTY);
  private static final Joiner PATH_JOINER = Joiner.on('/');

  private final Node root;

  private TargetTree(Node root) {
    this.root = root;
  }

  /** Returns the set of labels at the given path, excluding any labels in child packages. */
  public ImmutableSet<Label> get(Path packagePath) {
    return root.find(packagePath.iterator())
        .map(
            node ->
                node.targets().stream()
                    .map(name -> Label.fromPackageAndName(packagePath, name))
                    .collect(toImmutableSet()))
        .orElse(ImmutableSet.of());
  }

  public int size() {
    return root.size();
  }

  public boolean isEmpty() {
    return root.isEmpty();
  }

  /** Returns the entire tree as a set of labels. */
  public ImmutableSet<Label> toLabelSet() {
    return toMap().entries().stream()
        .map(e -> Label.fromPackageAndName(e.getKey(), e.getValue()))
        .collect(toImmutableSet());
  }

  /**
   * Returns the set of package paths in this tree. This is the set of paths which contain one or
   * more label.
   */
  public ImmutableSet<Path> toPathSet() {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
    root.visit(
        new ArrayDeque<>(),
        (path, labelNames) -> {
          if (!labelNames.isEmpty()) {
            builder.add(path.get());
          }
        });
    return builder.build();
  }

  /** Returns this tree as a map of (package path) -> (label names). */
  public ImmutableMultimap<Path, String> toMap() {
    ImmutableMultimap.Builder<Path, String> builder = ImmutableMultimap.builder();
    root.visit(new ArrayDeque<>(), (path, labelNames) -> builder.putAll(path.get(), labelNames));
    return builder.build();
  }

  public TargetTree getSubpackages(Path pkg) {
    return root.find(pkg.iterator())
        .map(node -> Node.forPath(pkg, node))
        .map(TargetTree::new)
        .orElse(TargetTree.EMPTY);
  }

  interface Visitor {
    void visit(Supplier<Path> path, ImmutableSet<String> labelNames);
  }

  @AutoValue
  abstract static class Node {

    static final Node EMPTY = new AutoValue_TargetTree_Node(ImmutableSet.of(), ImmutableMap.of());

    abstract ImmutableSet<String> targets();

    abstract ImmutableMap<String, Node> children();

    static Node create(ImmutableMap<String, Node> children, ImmutableSet<String> content) {
      return new AutoValue_TargetTree_Node(content, children);
    }

    /** Constructs a new node for the given path with an existing node as its only child. */
    static Node forPath(Path path, Node child) {
      // iterate backwards through the path elements to construct the new nodes bottom up, as
      // required the the immutable data structure.
      for (int i = path.getNameCount() - 1; i >= 0; i--) {
        child = Node.create(ImmutableMap.of(path.getName(i).toString(), child), ImmutableSet.of());
      }
      return child;
    }

    @Memoized
    int size() {
      return targets().size() + children().values().stream().mapToInt(Node::size).sum();
    }

    @Memoized
    boolean isEmpty() {
      return targets().isEmpty() && children().values().stream().allMatch(Node::isEmpty);
    }

    Optional<Node> find(Iterator<Path> path) {
      if (!path.hasNext()) {
        return Optional.of(this);
      }
      String childKey = path.next().toString();
      Node child = children().get(childKey);
      if (child == null) {
        return Optional.empty();
      }
      return child.find(path);
    }

    void visit(ArrayDeque<String> path, Visitor visitor) {
      visitor.visit(() -> Path.of(PATH_JOINER.join(path)), targets());
      for (Map.Entry<String, Node> e : children().entrySet()) {
        path.addLast(e.getKey());
        e.getValue().visit(path, visitor);
        path.removeLast();
      }
    }
  }

  /** Builder for {@link TargetTree}. */
  public static class Builder {
    private final ImmutableSet.Builder<String> content;
    private final Map<String, Builder> children = new HashMap<>();

    public Builder() {
      content = ImmutableSet.builder();
    }

    public TargetTree build() {
      return new TargetTree(buildNode());
    }

    Node buildNode() {
      ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
      for (Map.Entry<String, Builder> e : children.entrySet()) {
        builder.put(e.getKey(), e.getValue().buildNode());
      }
      return Node.create(builder.buildOrThrow(), content.build());
    }

    @CanIgnoreReturnValue
    public Builder add(Label target) {
      return add(target.getPackage().iterator(), target.getName().toString());
    }

    @CanIgnoreReturnValue
    public Builder add(Iterator<Path> pkg, String targetName) {
      if (!pkg.hasNext()) {
        content.add(targetName);
        return this;
      }

      children.computeIfAbsent(pkg.next().toString(), key -> new Builder()).add(pkg, targetName);
      return this;
    }
  }
}
