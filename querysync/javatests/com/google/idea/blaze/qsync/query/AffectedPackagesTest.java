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
import static com.google.idea.blaze.qsync.query.QuerySummaryTestUtil.createProtoForPackages;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.qsync.query.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AffectedPackagesTest {

  private static final Context TEST_CONTEXT =
      new Context() {
        @Override
        public <T extends Output> void output(T output) {}

        @Override
        public void setHasError() {}
      };

  @Test
  public void testModifyBuildFile() {
    QuerySummary query =
        new QuerySummary(
            createProtoForPackages("//my/build/package1:rule", "//my/build/package2:rule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package1/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isFalse();
    assertThat(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package1"));
    assertThat(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testAddBuildFile_siblingPackage() {
    QuerySummary query = new QuerySummary(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("my/build/package2/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isFalse();
    assertThat(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package2"));
    assertThat(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testAddBuildFile_childPackage() {
    QuerySummary query = new QuerySummary(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.ADD, Path.of("my/build/package1/subpackage/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isFalse();
    assertThat(affected.getModifiedPackages())
        .containsExactly(Path.of("my/build/package1"), Path.of("my/build/package1/subpackage"));
    assertThat(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testDeleteBuildFile_siblingPackage() {
    QuerySummary query =
        new QuerySummary(
            createProtoForPackages("//my/build/package1:rule1", "//my/build/package2:rule2"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.DELETE, Path.of("my/build/package2/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isFalse();
    assertThat(affected.getModifiedPackages()).isEmpty();
    assertThat(affected.getDeletedPackages()).containsExactly(Path.of("my/build/package2"));
  }

  @Test
  public void testDeleteBuildFile_childPackage() {
    QuerySummary query =
        new QuerySummary(
            createProtoForPackages(
                "//my/build/package1:rule1", "//my/build/package1/subpackage:subrule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(
                        Operation.DELETE, Path.of("my/build/package1/subpackage/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isFalse();
    assertThat(affected.getModifiedPackages()).containsExactly(Path.of("my/build/package1"));
    assertThat(affected.getDeletedPackages())
        .containsExactly(Path.of("my/build/package1/subpackage"));
  }

  @Test
  public void testModifyBuildFile_outsideProject() {
    QuerySummary query = new QuerySummary(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build/package1")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package2/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isTrue();
    assertThat(affected.getModifiedPackages()).isEmpty();
    assertThat(affected.getDeletedPackages()).isEmpty();
  }

  @Test
  public void testModifyBuildFile_excluded() {
    QuerySummary query = new QuerySummary(createProtoForPackages("//my/build/package1:rule"));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .lastQuery(query)
            .projectIncludes(ImmutableList.of(Path.of("my/build")))
            .projectExcludes(ImmutableList.of(Path.of("my/build/package2")))
            .changedFiles(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("my/build/package2/BUILD"))))
            .build(TEST_CONTEXT);
    assertThat(affected.isEmpty()).isTrue();
    assertThat(affected.getModifiedPackages()).isEmpty();
    assertThat(affected.getDeletedPackages()).isEmpty();
  }
}
