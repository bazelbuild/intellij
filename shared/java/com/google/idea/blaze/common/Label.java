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
package com.google.idea.blaze.common;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents an absolute build target label.
 *
 * <p>This class is a simple wrapper around a string and should be used in place of {@link String}
 * whenever appropriate.
 *
 * <p>It can be considered equivalent to <a href="https://bazel.build/rules/lib/Label>Label</a> in
 * bazel.
 *
 * <p>Note that this class only supports labels in the current workspace, i.e. not labels of the
 * form {@code @repo//pkg/foo:abc}.
 */
public class Label {

  private final String label;

  public static Label of(String label) {
    return new Label(label);
  }

  public static Label fromPackageAndName(Path packagePath, Path name) {
    return of(String.format("//%s:%s", packagePath, name));
  }

  public static Label fromPackageAndName(Path packagePath, String name) {
    return fromPackageAndName(packagePath, Path.of(name));
  }

  public static ImmutableList<Label> toLabelList(List<String> labels) {
    return labels.stream().map(Label::of).collect(toImmutableList());
  }

  public Label(String label) {
    // If this is causing problems, it's probably time to support explicit workspace names here.
    // Users of getPackage() may need to be updated to check the workspace too:
    Preconditions.checkArgument(
        !label.startsWith("@"), "Explicit workspace names not supported: %s", label);
    Preconditions.checkArgument(label.startsWith("//"), label);
    Preconditions.checkArgument(label.contains(":"), label);
    this.label = label;
  }

  public Path getPackage() {
    // this should be safe thanks to the asserts in the constructor.
    return Path.of(label.substring(2, label.indexOf(":")));
  }

  public Path getName() {
    // this should be safe thanks to the asserts in the constructor.
    return Path.of(label.substring(label.indexOf(':') + 1));
  }

  /** When this label refers to a source file, returns the workspace relative path to that file. */
  public Path toFilePath() {
    return getPackage().resolve(getName());
  }

  @Override
  public String toString() {
    return label;
  }

  @Override
  public boolean equals(Object that) {
    if (!(that instanceof Label)) {
      return false;
    }
    return ((Label) that).label.equals(this.label);
  }

  @Override
  public int hashCode() {
    return label.hashCode();
  }
}
