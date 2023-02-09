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
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.EMPTY_PACKAGE_READER;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GraphToProjectConverterTest {

  @Test
  public void testCalculateRootSources_singleSource_atImportRoot() throws IOException {

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_singleSource_belowImportRoot() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/subpackage/Class1.java"), "com.test.subpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_belowImportRoot() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package1/Class1.java"), "com.test.package1",
            Path.of("java/com/test/package2/Class2.java"), "com.test.package2");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_multiRoots() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/app/AppClass.java"), "com.app",
            Path.of("java/com/lib/LibClass.java"), "com.lib");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/app"), Path.of("java/com/lib")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/app", "java/com/lib");
    assertThat(rootSources.get("java/com/app")).containsExactly("", "com.app");
    assertThat(rootSources.get("java/com/lib")).containsExactly("", "com.lib");
  }

  @Test
  public void testCalculateRootSources_multiSource_packageMismatch() throws IOException {
    // TODO(b/266538303) this test will fail if we swap `package1` and `package2` (i.e. such that
    //  their lexigraphic order is reversed), due to issues in GraphToProjectConverter. Fix those
    //  issues and add more test cases accordingly
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.oddpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test"))
        .containsExactly(
            "", "com.test",
            "package1", "com.test.oddpackage");
  }

  @Test
  public void testCalculateRootSources_multiSource_repackagedSource() throws IOException {
    // TODO(b/266538303) This test would fail if the lexicographic order of `repackaged` and
    //  `somepackage` was reversed, due to issues in GraphToProjectConverter
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/repackaged/com/foo/Class1.java"), "com.foo",
            Path.of("java/com/test/somepackage/Class2.java"), "com.test.somepackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test"))
        .containsExactly(
            "repackaged", "",
            "", "com.test");
  }

  @Test
  public void testCalculateAndroidResourceDirectories_single_directory() {
    ImmutableList<Path> sourceFiles =
        ImmutableList.of(
            Path.of("java/com/test/AndroidManifest.xml"),
            Path.of("java/com/test/res/values/strings.xml"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories).containsExactly(Path.of("java/com/test/res"));
  }

  @Test
  public void testCalculateAndroidResourceDirectories_multiple_directories() {
    ImmutableList<Path> sourceFiles =
        ImmutableList.of(
            Path.of("java/com/test/AndroidManifest.xml"),
            Path.of("java/com/test/res/values/strings.xml"),
            Path.of("java/com/test2/AndroidManifest.xml"),
            Path.of("java/com/test2/res/layout/some-activity.xml"),
            Path.of("java/com/test2/res/layout/another-activity.xml"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories)
        .containsExactly(Path.of("java/com/test/res"), Path.of("java/com/test2/res"));
  }

  @Test
  public void testCalculateAndroidResourceDirectories_manifest_without_res_directory() {
    ImmutableList<Path> sourceFiles =
        ImmutableList.of(
            Path.of("java/com/nores/AndroidManifest.xml"), Path.of("java/com/nores/Foo.java"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories).isEmpty();
  }

  @Test
  public void testCalculateAndroidSourcePackages_rootWithEmptyPrefix() {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER, NOOP_CONTEXT, ImmutableList.of(), ImmutableList.of());

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("java/com/example/foo/Foo.java"), Path.of("java/com/example/bar/Bar.java"));
    ImmutableMap<String, Map<String, String>> rootToPrefix =
        ImmutableMap.of("java/com/example", ImmutableMap.of("", "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootWithPrefix() {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER, NOOP_CONTEXT, ImmutableList.of(), ImmutableList.of());

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("some_project/java/com/example/bar/Bar.java"));
    ImmutableMap<String, Map<String, String>> rootToPrefix =
        ImmutableMap.of("some_project", ImmutableMap.of("java", ""));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootAndNonEmptyRoot() {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER, NOOP_CONTEXT, ImmutableList.of(), ImmutableList.of());

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("java/com/example/bar/Bar.java"));
    ImmutableMap<String, Map<String, String>> rootToPrefix =
        ImmutableMap.of(
            "some_project",
            ImmutableMap.of("java", ""),
            "java/com/example",
            ImmutableMap.of("", "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }
}
