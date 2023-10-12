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
package com.google.idea.blaze.base.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.qsync.DependencyTracker.RequestedTargets;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyTrackerImplTest {

  public final BlazeContext context = BlazeContext.create();
  public final TestDataSyncRunner syncRunner =
      new TestDataSyncRunner(context, QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  @Test
  public void computeRequestedTargets_srcFile() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    Iterables.getOnlyElement(
                            TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getRelativeSourcePaths())
                        .resolve(Path.of("TestClassExternalDep.java")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    Path targetName = Iterables.getOnlyElement(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.srcPaths);
    assertThat(targets.get().buildTargets)
        .containsExactly(Label.fromPackageAndName(TestData.ROOT.resolve(targetName), targetName));
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_buildFile_multiTarget() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_MULTI_TARGETS);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    Iterables.getOnlyElement(
                            TestData.JAVA_LIBRARY_MULTI_TARGETS.getRelativeSourcePaths())
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    Path targetName = Iterables.getOnlyElement(TestData.JAVA_LIBRARY_MULTI_TARGETS.srcPaths);
    assertThat(targets.get().buildTargets)
        .containsExactly(
            Label.fromPackageAndName(TestData.ROOT.resolve(targetName), "externaldep"),
            Label.fromPackageAndName(TestData.ROOT.resolve(targetName), "nodeps"));
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_buildFile_nested() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_NESTED_PACKAGE);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    Iterables.getOnlyElement(
                            TestData.JAVA_LIBRARY_NESTED_PACKAGE.getRelativeSourcePaths())
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    Path targetName = Iterables.getOnlyElement(TestData.JAVA_LIBRARY_NESTED_PACKAGE.srcPaths);
    assertThat(targets.get().buildTargets)
        .containsExactly(Label.fromPackageAndName(TestData.ROOT.resolve(targetName), targetName));
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_directory() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_NESTED_PACKAGE);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    Iterables.getOnlyElement(
                        TestData.JAVA_LIBRARY_NESTED_PACKAGE.getRelativeSourcePaths()))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    Path targetName = Iterables.getOnlyElement(TestData.JAVA_LIBRARY_NESTED_PACKAGE.srcPaths);
    assertThat(targets.get().buildTargets)
        .containsExactly(
            Label.fromPackageAndName(TestData.ROOT.resolve(targetName), targetName),
            Label.fromPackageAndName(TestData.ROOT.resolve(targetName).resolve("inner"), "inner"));
    assertThat(targets.get().expectedDependencyTargets)
        .containsExactly(
            Label.of("@com_google_guava_guava//jar:jar"),
            Label.of("@gson//jar:jar"));
  }
}
