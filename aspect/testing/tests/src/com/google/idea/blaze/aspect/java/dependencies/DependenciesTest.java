/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.java.dependencies;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that the aspect propagates over dependencies and exports. */
@RunWith(JUnit4.class)
public class DependenciesTest extends BazelIntellijAspectTest {

  @Test
  public void testJavaLibraryWithDependencies() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":single_dep_fixture");
    TargetIdeInfo fooTarget = findTarget(testFixture, ":foo");
    assertThat(fooTarget).isNotNull();

    TargetIdeInfo depTarget = findTarget(testFixture, ":single_dep");
    assertThat(dependenciesForTarget(depTarget)).contains(dep(":foo"));
  }

  @Test
  public void testJavaLibraryWithTransitiveDependencies() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":transitive_dep_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":transitive_dep");
    assertThat(dependenciesForTarget(target)).contains(dep(":single_dep"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("foo.java-manifest"),
            testRelative(intellijInfoFileName("foo")),
            testRelative("single_dep.java-manifest"),
            testRelative(intellijInfoFileName("single_dep")),
            testRelative("transitive_dep.java-manifest"),
            testRelative(intellijInfoFileName("transitive_dep")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsAllOf(
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libsingle_dep-hjar.jar"),
            testRelative("libsingle_dep-src.jar"),
            testRelative("libtransitive_dep-hjar.jar"),
            testRelative("libtransitive_dep-src.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsAllOf(
            testRelative("libfoo.jar"),
            testRelative("libsingle_dep.jar"),
            testRelative("libtransitive_dep.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }

  @Test
  public void testJavaLibraryWithDiamondDependencies() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":diamond_dep_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":diamond_dep");
    assertThat(dependenciesForTarget(target))
        .containsAllOf(dep(":single_dep"), dep(":single_dep_sibling"));
  }

  @Test
  public void testJavaLibraryWithExports() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":export_fixture");
    TargetIdeInfo foo = findTarget(testFixture, ":foo");
    TargetIdeInfo fooExporter = findTarget(testFixture, ":foo_exporter");
    TargetIdeInfo exportConsumer = findTarget(testFixture, ":export_consumer");

    assertThat(foo).isNotNull();
    assertThat(dependenciesForTarget(fooExporter)).contains(dep(":foo"));

    assertThat(dependenciesForTarget(exportConsumer))
        .containsAllOf(dep(":foo_exporter"), dep(":foo"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("foo.java-manifest"),
            testRelative(intellijInfoFileName("foo")),
            testRelative("foo_exporter.java-manifest"),
            testRelative(intellijInfoFileName("foo_exporter")),
            testRelative("export_consumer.java-manifest"),
            testRelative(intellijInfoFileName("export_consumer")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsAllOf(
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libfoo_exporter-hjar.jar"),
            testRelative("libfoo_exporter-src.jar"),
            testRelative("libexport_consumer-hjar.jar"),
            testRelative("libexport_consumer-src.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsAllOf(
            testRelative("libfoo.jar"),
            testRelative("libfoo_exporter.jar"),
            testRelative("libexport_consumer.jar"));
  }

  @Test
  public void testJavaLibraryWithTransitiveExports() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":transitive_export_fixture");
    TargetIdeInfo transitiveExportConsumer = findTarget(testFixture, ":transitive_export_consumer");

    assertThat(dependenciesForTarget(transitiveExportConsumer))
        .containsAllOf(dep(":foo"), dep(":foo_exporter"), dep(":foo_exporter_exporter"));
  }

  @Test
  public void testRuntimeDeps() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":runtime_dep_lib_fixture");

    // Make sure we propagated to foo
    TargetIdeInfo foo = findTarget(testFixture, ":foo");
    assertThat(foo).isNotNull();

    // Make sure foo is added to runtime_deps
    TargetIdeInfo lib = findTarget(testFixture, ":runtime_dep_lib");
    assertThat(dependenciesForTarget(lib)).contains(runtimeDep(":foo"));
  }
}
