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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.testdata.TestData;
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
  public void getPendingExternalDeps_noSnapshot() {
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
    assertThat(dt.getPendingExternalDeps(ImmutableSet.of(Label.of("//some/package:target"))))
        .isEmpty();
  }

  @Test
  public void getPendingExternalDeps_followJavaDeps_noneBuilt() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of());
    String expected = "@com_google_guava_guava//jar:jar";

    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .containsExactly(Label.of(expected));
  }

  @Test
  public void getPendingExternalDeps_followJavaDeps_allBuilt() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
    String guava = "@com_google_guava_guava//jar:jar";

    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of(Label.of(guava)));
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .isEmpty();
  }

  @Test
  public void getPendingExternalDeps_ccTarget_returnSelf() throws Exception {
    BlazeProjectSnapshot snapshot = syncRunner.sync(TestData.CC_LIBRARY_QUERY);
    blazeProject.setCurrent(context, snapshot);
    DependencyTrackerImpl dt =
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
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
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
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
        new DependencyTrackerImpl(blazeProject, dependencyBuilder, artifactTracker);
    when(artifactTracker.getLiveCachedTargets()).thenReturn(ImmutableSet.of());
    assertThat(
            dt.getPendingExternalDeps(
                ImmutableSet.copyOf(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedLabels())))
        .containsExactlyElementsIn(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedLabels());
  }
}
