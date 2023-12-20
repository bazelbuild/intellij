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
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.REPOSITORY_MAPPED_LABEL_CORRESPONDENCE;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.qsync.DependencyTracker.RequestedTargets;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class DependencyTrackerImplTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  public final BlazeContext context = BlazeContext.create();
  public final TestDataSyncRunner syncRunner =
      new TestDataSyncRunner(context, QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  public final BlazeProject blazeProject = new BlazeProject();
  @Mock DependencyBuilder dependencyBuilder;
  @Mock ArtifactTracker artifactTracker;

  @Test
  public void computeRequestedTargets_srcFile() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
                        .getOnlySourcePath()
                        .resolve(Path.of("TestClassExternalDep.java")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    assertThat(targets.get().expectedDependencyTargets)
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
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
                    TestData.JAVA_LIBRARY_MULTI_TARGETS
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(
            TestData.JAVA_LIBRARY_MULTI_TARGETS
                .getAssumedOnlyLabel()
                .siblingWithName("externaldep"),
            TestData.JAVA_LIBRARY_MULTI_TARGETS.getAssumedOnlyLabel().siblingWithName("nodeps"));
    assertThat(targets.get().expectedDependencyTargets)
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
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
                    TestData.JAVA_LIBRARY_NESTED_PACKAGE
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
        .containsExactly(TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel());
    assertThat(targets.get().expectedDependencyTargets)
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_directory() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_NESTED_PACKAGE);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context, snapshot, TestData.JAVA_LIBRARY_NESTED_PACKAGE.getOnlySourcePath())
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(
            TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel(),
            TestData.JAVA_LIBRARY_NESTED_PACKAGE
                .getAssumedOnlyLabel()
                .siblingWithPathAndName("inner:inner"));
    assertThat(targets.get().expectedDependencyTargets)
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
        .containsExactly(
            Label.of("@com_google_guava_guava//jar:jar"),
            Label.of("@com_google_code_gson_gson//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_cc_srcFile() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.CC_EXTERNAL_DEP_QUERY);
    Optional<RequestedTargets> targets =
        DependencyTrackerImpl.computeRequestedTargets(
            snapshot,
            DependencyTrackerImpl.getProjectTargets(
                    context,
                    snapshot,
                    TestData.CC_EXTERNAL_DEP_QUERY.getOnlySourcePath().resolve("TestClass.cc"))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets).isPresent();
    assertThat(targets.get().buildTargets)
        .containsExactly(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    assertThat(targets.get().expectedDependencyTargets).isEmpty();
  }

  @Test
  public void getPendingExternalDeps_noSnapshot() {
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    assertThat(dt.getPendingExternalDeps(ImmutableSet.of(Label.of("//some/package:target"))))
        .isEmpty();
  }

  @Test
  public void getPendingExternalDeps_followJavaDeps_noneBuilt() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of());
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void getPendingExternalDeps_followJavaDeps_allBuilt() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets())
        .thenReturn(ImmutableSet.of(Label.of("@com_google_guava_guava//jar:jar")));
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .comparingElementsUsing(REPOSITORY_MAPPED_LABEL_CORRESPONDENCE)
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void getPendingExternalDeps_ccTarget_returnSelf() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.CC_LIBRARY_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of());
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.CC_LIBRARY_QUERY.getAssumedLabels())))
        .containsExactlyElementsIn(TestData.CC_LIBRARY_QUERY.getAssumedLabels());
  }

  @Test
  public void getPendingExternalDeps_ccTarget_alreadyBuilt_returnEmpty() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.CC_LIBRARY_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets())
        .thenReturn(ImmutableSet.copyOf(TestData.CC_LIBRARY_QUERY.getAssumedLabels()));
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.CC_LIBRARY_QUERY.getAssumedLabels())))
        .isEmpty();
  }

  @Test
  public void getPendingExternalDeps_ccTarget_externalDepsIgnored() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.CC_EXTERNAL_DEP_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(null, blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of());
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .containsExactlyElementsIn(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedLabels());
  }
}
