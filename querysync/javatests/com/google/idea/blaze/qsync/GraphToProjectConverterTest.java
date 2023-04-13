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
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentRoot.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("DuplicateExpressions")
@RunWith(JUnit4.class)
public class GraphToProjectConverterTest {

  @Test
  public void testChooseFilePerPackage() {
    ImmutableSet<Path> buildFiles =
        ImmutableSet.of(
            Path.of("java/com/test/BUILD"),
            Path.of("java/com/test/nested/BUILD"),
            Path.of("java/com/double/BUILD"),
            Path.of("java/com/multiple/BUILD"));
    ImmutableSet<Path> files =
        ImmutableSet.of(
            Path.of("java/com/test/Class1.java"),
            Path.of("java/com/test/nested/Nested.java"),
            Path.of("java/com/double/nest1/BothThis.java"),
            Path.of("java/com/double/nest2/AndThis.java"),
            Path.of("java/com/multiple/AThis.java"),
            Path.of("java/com/multiple/BNotThis.java"),
            Path.of("java/com/multiple/nest/BNorThis.java"));

    List<Path> chosenFiles = GraphToProjectConverter.chooseTopLevelFiles(files, buildFiles);

    assertThat(chosenFiles)
        .containsExactly(
            Path.of("java/com/test/Class1.java"),
            Path.of("java/com/test/nested/Nested.java"),
            Path.of("java/com/double/nest1/BothThis.java"),
            Path.of("java/com/double/nest2/AndThis.java"),
            Path.of("java/com/multiple/AThis.java"));
  }

  @Test
  public void testSplitByRoot() {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    ImmutableSet<Path> roots = ImmutableSet.of(Path.of("java"), Path.of("javatests"));
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get, NOOP_CONTEXT, ProjectDefinition.create(roots, ImmutableSet.of()));

    ImmutableMap<Path, String> prefixes =
        ImmutableMap.of(
            Path.of("java/com/test"), "com.test",
            Path.of("java/com/test/nested"), "com.test.nested",
            Path.of("java/com/root"), "",
            Path.of("javatests/com/one"), "prefix.com",
            Path.of("javatests/com/two"), "other.prefix");

    Map<Path, Map<Path, String>> split = converter.splitByRoot(prefixes);

    assertThat(split.keySet()).containsExactlyElementsIn(roots);
    assertThat(split.get(Path.of("java")))
        .containsExactly(
            Path.of("com/test"), "com.test",
            Path.of("com/test/nested"), "com.test.nested",
            Path.of("com/root"), "");
    assertThat(split.get(Path.of("javatests")))
        .containsExactly(
            Path.of("com/one"), "prefix.com",
            Path.of("com/two"), "other.prefix");
  }

  @Test
  public void testMergeCompatibleSourceRoots() {
    Map<Path, Map<Path, String>> roots = new HashMap<>();
    Map<Path, String> java = new HashMap<>();
    java.put(Path.of("a/b/c/d"), "com.google.d");
    java.put(Path.of("a/b/c/e"), "com.google.e");
    roots.put(Path.of("java"), java);

    Map<Path, String> javatests = new HashMap<>();
    javatests.put(Path.of("compatible/a/b/c/d"), "com.google.d");
    javatests.put(Path.of("compatible/a/b/c/d/e"), "com.google.d.e");
    javatests.put(Path.of("incompatible/a"), "com.odd");
    javatests.put(Path.of("incompatible/a/b/c"), "com.google.a.b.c");
    roots.put(Path.of("javatests"), javatests);

    GraphToProjectConverter.mergeCompatibleSourceRoots(roots);

    assertThat(roots.keySet()).containsExactly(Path.of("java"), Path.of("javatests"));
    assertThat(roots.get(Path.of("java"))).containsExactly(Path.of("a/b/c"), "com.google");
    assertThat(roots.get(Path.of("javatests")))
        .containsExactly(
            Path.of("compatible/a/b/c"), "com.google",
            Path.of("incompatible/a"), "com.odd",
            Path.of("incompatible/a/b"), "com.google.a.b");
  }

  @Test
  public void testCalculateRootSources_singleSource_atImportRoot() throws IOException {
    ImmutableSet<Path> packages = ImmutableSet.of(Path.of("java/com/test/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_singleSource_belowImportRoot() throws IOException {
    ImmutableSet<Path> packages = ImmutableSet.of(Path.of("java/com/test/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/subpackage/Class1.java"), "com.test.subpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_belowImportRoot() throws IOException {
    ImmutableSet<Path> packages = ImmutableSet.of(Path.of("java/com/test/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package1/Class1.java"), "com.test.package1",
            Path.of("java/com/test/package2/Class2.java"), "com.test.package2");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiRoots() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("java/com/app/BUILD"), Path.of("java/com/lib/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/app/AppClass.java"), "com.app",
            Path.of("java/com/lib/LibClass.java"), "com.lib");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(
                ImmutableSet.of(Path.of("java/com/app"), Path.of("java/com/lib")),
                ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet())
        .containsExactly(Path.of("java/com/app"), Path.of("java/com/lib"));
    assertThat(rootSources.get(Path.of("java/com/app"))).containsExactly(Path.of(""), "com.app");
    assertThat(rootSources.get(Path.of("java/com/lib"))).containsExactly(Path.of(""), "com.lib");
  }

  @Test
  public void testCalculateRootSources_multiSource_packageMismatch() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("java/com/test/BUILD"), Path.of("java/com/test/package1/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.oddpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of(""), "com.test",
            Path.of("package1"), "com.test.oddpackage");
  }

  @Test
  public void testCalculateRootSources_multiSource_samePrefix() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(
            Path.of("java/com/test/package1/BUILD"), Path.of("java/com/test/package2/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.package1");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_nextedPrefixCompatible() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("java/com/test/BUILD"), Path.of("java/com/test/package/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/Class1.java"), "com.test",
            Path.of("java/com/test/package/Class2.java"), "com.test.package");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_nestedPrefixIncompatible() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("java/com/test/BUILD"), Path.of("java/com/test/package/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/Class1.java"), "com.test.odd",
            Path.of("java/com/test/package/Class2.java"), "com.test.package");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of(""), "com.test.odd",
            Path.of("package"), "com.test.package");
  }

  @Test
  public void testCalculateRootSources_multiSource_rootPrefix() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("third_party/java/BUILD"), Path.of("third_party/javatests/BUILD"));

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("third_party/java/com/test/Class1.java"), "com.test",
            Path.of("third_party/javatests/com/test/Class2.java"), "com.test");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("third_party")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("third_party"));
    assertThat(rootSources.get(Path.of("third_party")))
        .containsExactly(
            Path.of("java"), "",
            Path.of("javatests"), "");
  }

  @Test
  public void testCalculateRootSources_multiSource_repackagedSource() throws IOException {
    ImmutableSet<Path> packages =
        ImmutableSet.of(Path.of("java/com/test/BUILD"), Path.of("java/com/test/repackaged/BUILD"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/repackaged/com/foo/Class1.java"), "com.foo",
            Path.of("java/com/test/somepackage/Class2.java"), "com.test.somepackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(Path.of("java/com/test")), ImmutableSet.of()));

    Map<Path, Map<Path, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of("repackaged"), "",
            Path.of(""), "com.test");
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
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(), ImmutableSet.of()));

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("java/com/example/foo/Foo.java"), Path.of("java/com/example/bar/Bar.java"));
    Map<Path, Map<Path, String>> rootToPrefix =
        ImmutableMap.of(Path.of("java/com/example"), ImmutableMap.of(Path.of(""), "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootWithPrefix() {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(), ImmutableSet.of()));

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("some_project/java/com/example/bar/Bar.java"));
    Map<Path, Map<Path, String>> rootToPrefix =
        ImmutableMap.of(Path.of("some_project"), ImmutableMap.of(Path.of("java"), ""));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootAndNonEmptyRoot() {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(), ImmutableSet.of()));

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("java/com/example/bar/Bar.java"));
    Map<Path, Map<Path, String>> rootToPrefix =
        ImmutableMap.of(
            Path.of("some_project"),
            ImmutableMap.of(Path.of("java"), ""),
            Path.of("java/com/example"),
            ImmutableMap.of(Path.of(""), "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testConvertProject_emptyProject() throws IOException {
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(), ImmutableSet.of()));
    ProjectProto.Project project = converter.createProject(BuildGraphData.EMPTY);
    assertThat(project.getModulesCount()).isEqualTo(1);

    ProjectProto.Module workspaceModule = project.getModules(0);
    assertThat(workspaceModule.getName()).isEqualTo(".workspace");

    assertThat(workspaceModule.getContentEntriesCount()).isEqualTo(1);
    ProjectProto.ContentEntry generatedSourceRoot = workspaceModule.getContentEntries(0);
    assertThat(generatedSourceRoot.getRoot().getBase()).isEqualTo(Base.PROJECT);
    assertThat(generatedSourceRoot.getRoot().getPath()).isEqualTo(".blaze/generated");

    assertThat(generatedSourceRoot.getSourcesCount()).isEqualTo(1);
    ProjectProto.SourceFolder generatedSourceFolder = generatedSourceRoot.getSources(0);
    assertThat(generatedSourceFolder.getPath()).isEqualTo(".blaze/generated");
    assertThat(generatedSourceFolder.getIsGenerated()).isTrue();
    assertThat(generatedSourceFolder.getIsTest()).isFalse();
  }

  @Test
  public void testConvertProject_buildGraphWithSingleImportRoot() throws IOException {
    Path workspaceImportDirectory = TestData.ROOT.resolve("nodeps");
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(ImmutableSet.of(workspaceImportDirectory), ImmutableSet.of()));

    BuildGraphData buildGraphData =
        new BlazeQueryParser(NOOP_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY));
    ProjectProto.Project project = converter.createProject(buildGraphData);

    // Sanity check
    assertThat(project.getModulesCount()).isEqualTo(1);
    ProjectProto.Module workspaceModule = project.getModules(0);

    assertThat(workspaceModule.getContentEntriesCount()).isEqualTo(2);

    // Sanity check
    ProjectProto.ContentEntry generatedSourceRoot = workspaceModule.getContentEntries(0);
    assertThat(generatedSourceRoot.getRoot().getBase()).isEqualTo(Base.PROJECT);
    assertThat(generatedSourceRoot.getRoot().getPath()).isEqualTo(".blaze/generated");
    assertThat(generatedSourceRoot.getSourcesCount()).isEqualTo(1);

    ProjectProto.ContentEntry javaContentEntry = workspaceModule.getContentEntries(1);
    assertThat(javaContentEntry.getRoot().getBase()).isEqualTo(Base.WORKSPACE);
    assertThat(javaContentEntry.getRoot().getPath()).isEqualTo(workspaceImportDirectory.toString());
    assertThat(javaContentEntry.getSourcesCount()).isEqualTo(1);

    ProjectProto.SourceFolder javaSource = javaContentEntry.getSources(0);
    assertThat(javaSource.getPath()).isEqualTo(workspaceImportDirectory.toString());
    assertThat(javaSource.getIsGenerated()).isFalse();
    assertThat(javaSource.getIsTest()).isFalse();
  }
}
