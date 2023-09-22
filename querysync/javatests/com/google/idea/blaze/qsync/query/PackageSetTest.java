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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PackageSetTest {

  @Test
  public void test_contains() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));

    assertThat(ps.contains(Path.of("a/b/c"))).isTrue();
    assertThat(ps.contains(Path.of("a/b"))).isFalse();
  }

  @Test
  public void test_size() {
    PackageSet ps = TreePackageSet.of();
    assertThat(ps.size()).isEqualTo(0);

    ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.size()).isEqualTo(2);
  }

  @Test
  public void test_isEmpty() {
    PackageSet ps = TreePackageSet.of();
    assertThat(ps.isEmpty()).isTrue();

    ps = TreePackageSet.of(Path.of("a/b"));
    assertThat(ps.isEmpty()).isFalse();
  }

  @Test
  public void test_asPathSet() {
    PackageSet ps = TreePackageSet.of();
    assertThat(ps.asPathSet()).isEmpty();

    ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.asPathSet()).containsExactly(Path.of("a/b/c"), Path.of("a/b/d"));
  }

  @Test
  public void test_getParentPackage_noParent() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.getParentPackage(Path.of("a/b/c"))).isEmpty();
  }

  @Test
  public void test_getParentPackage_directParent() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.getParentPackage(Path.of("a/b/c/d"))).hasValue(Path.of("a/b/c"));
  }

  @Test
  public void test_getParentPackage_indirectParent() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"));
    assertThat(ps.getParentPackage(Path.of("a/b/c/d/e"))).hasValue(Path.of("a/b/c"));
  }

  @Test
  public void test_findIncludingPackage_notFound() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"));
    assertThat(ps.findIncludingPackage(Path.of("a/b/d"))).isEmpty();
  }

  @Test
  public void test_findIncludingPackage_self() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.findIncludingPackage(Path.of("a/b/d"))).hasValue(Path.of("a/b/d"));
  }

  @Test
  public void test_findIncludingPackage_directParent() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.findIncludingPackage(Path.of("a/b/d/e"))).hasValue(Path.of("a/b/d"));
  }

  @Test
  public void test_findIncludingPackage_indirectParent() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"));
    assertThat(ps.findIncludingPackage(Path.of("a/b/d/e/f"))).hasValue(Path.of("a/b/d"));
  }

  @Test
  public void test_getSubpackages_selfAndChildren() {
    PackageSet ps =
        TreePackageSet.of(Path.of("a/b"), Path.of("a/b/c"), Path.of("a/b/d"), Path.of("z/y/z"));
    assertThat(ps.getSubpackages(Path.of("a/b")).asPathSet())
        .containsExactly(Path.of("a/b"), Path.of("a/b/c"), Path.of("a/b/d"));
  }

  @Test
  public void test_getSubpackages_directChildren() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"), Path.of("x/y/z"));
    assertThat(ps.getSubpackages(Path.of("a/b")).asPathSet())
        .containsExactly(Path.of("a/b/c"), Path.of("a/b/d"));
  }

  @Test
  public void test_getSubpackages_indirectChildren() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"), Path.of("x/y/z"));
    assertThat(ps.getSubpackages(Path.of("a")).asPathSet())
        .containsExactly(Path.of("a/b/c"), Path.of("a/b/d"));
  }

  @Test
  public void test_getSubpackages_none() {
    PackageSet ps = TreePackageSet.of(Path.of("a/b/c"), Path.of("a/b/d"), Path.of("x/y/z"));
    assertThat(ps.getSubpackages(Path.of("b")).asPathSet()).isEmpty();
  }

  @Test
  public void test_getSubpackages_subTree() {
    PackageSet ps =
        TreePackageSet.of(
            Path.of("a/b/c"),
            Path.of("a/b/d"),
            Path.of("a/b/c/d"),
            Path.of("a/b/c/e"),
            Path.of("x/y/z"));
    assertThat(ps.getSubpackages(Path.of("a/b")).asPathSet())
        .containsExactly(
            Path.of("a/b/c"), Path.of("a/b/d"), Path.of("a/b/c/d"), Path.of("a/b/c/e"));
  }
}
