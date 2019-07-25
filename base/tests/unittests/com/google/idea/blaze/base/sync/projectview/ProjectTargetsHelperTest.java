/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectview.ImportRoots.ProjectDirectoriesHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ProjectTargetsHelper} */
@RunWith(JUnit4.class)
public class ProjectTargetsHelperTest extends BlazeTestCase {

  @Test
  public void testAllInPackageWildcardTargetsHandled() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.create(ImmutableList.of(TargetExpression.fromString("//foo:all")));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isFalse();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testRecursiveWildcardTargetsHandled() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.create(ImmutableList.of(TargetExpression.fromString("//foo/...")));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//foo/bar/baz:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isFalse();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("foo/bar"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testSingleTargetHandled() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.create(ImmutableList.of(TargetExpression.fromString("//foo:target")));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//foo:other"))).isFalse();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isFalse();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isFalse();
    assertThat(helper.packageInProject(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testLaterTargetsOverrideEarlierTargets() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.create(
            ImmutableList.of(
                TargetExpression.fromString("//foo:target"),
                TargetExpression.fromString("-//foo:target"),
                TargetExpression.fromString("-//bar:target"),
                TargetExpression.fromString("//bar:all")));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isFalse();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isTrue();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isFalse();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isTrue();
  }

  @Test
  public void testInferringTargetsFromDirectoriesSimple() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of()));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//foo/bar:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isFalse();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testExplictTargetsOverrideInferredTargets() throws Exception {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(
                TargetExpression.fromString("-//foo:target"),
                TargetExpression.fromString("//bar:target")),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of(new WorkspacePath("bar"))));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isFalse();
    assertThat(helper.targetInProject(Label.create("//foo:other"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//foo/bar:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//bar:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//bar:other"))).isFalse();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("bar"))).isFalse();
  }

  @Test
  public void testInferredTargetsWithExcludedSubdirectories() {
    ProjectTargetsHelper helper =
        ProjectTargetsHelper.createWithTargetsDerivedFromDirectories(
            ImmutableList.of(),
            new ProjectDirectoriesHelper(
                /* rootDirectories= */ ImmutableList.of(new WorkspacePath("foo")),
                /* excludeDirectories= */ ImmutableSet.of(new WorkspacePath("foo/bar"))));

    assertThat(helper.targetInProject(Label.create("//foo:target"))).isTrue();
    assertThat(helper.targetInProject(Label.create("//foo/bar:target"))).isFalse();
    assertThat(helper.targetInProject(Label.create("//foo/other:target"))).isTrue();

    assertThat(helper.packageInProject(new WorkspacePath("foo"))).isTrue();
    assertThat(helper.packageInProject(new WorkspacePath("foo/bar"))).isFalse();
    assertThat(helper.packageInProject(new WorkspacePath("foo/other"))).isTrue();
  }
}
