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
public class QueryStateTest {

  private static final Context TEST_CONTEXT =
      new Context() {
        @Override
        public <T extends Output> void output(T output) {}

        @Override
        public void setHasError() {}
      };

  @Test
  public void testDeltaUpdate_upstreamRevisionChange() {
    QueryState state =
        QueryState.builder()
            .queryOutput(QuerySummary.EMPTY)
            .includePaths(ImmutableList.of())
            .upstreamRevision("1")
            .workingSet(ImmutableSet.of())
            .build();

    assertThat(state.deltaUpdate("2", ImmutableSet.of(), TEST_CONTEXT)).isNull();
  }

  @Test
  public void testDeltaUpdate_buildFileAddedThenReverted() {
    QueryState state =
        QueryState.builder()
            .queryOutput(
                new QuerySummary(
                    QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")))
            .includePaths(ImmutableList.of(Path.of("package")))
            .upstreamRevision("2")
            .workingSet(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.ADD, Path.of("package/path/BUILD"))))
            .build();

    // new working set does not include added build file, so package is deleted
    AffectedPackages affected = state.deltaUpdate("2", ImmutableSet.of(), TEST_CONTEXT);
    assertThat(affected).isNotNull();
    assertThat(affected.getDeletedPackages()).containsExactly(Path.of("package/path"));
  }

  @Test
  public void testDeltaUpdate_buildFileDeletedThenReverted() {
    QueryState state =
        QueryState.builder()
            .queryOutput(QuerySummary.EMPTY)
            .includePaths(ImmutableList.of(Path.of("package")))
            .upstreamRevision("2")
            .workingSet(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.DELETE, Path.of("package/path/BUILD"))))
            .build();

    // new working set does not include deleted build file, so package is re-added
    AffectedPackages affected = state.deltaUpdate("2", ImmutableSet.of(), TEST_CONTEXT);
    assertThat(affected).isNotNull();
    assertThat(affected.getModifiedPackages()).containsExactly(Path.of("package/path"));
  }

  @Test
  public void testDeltaUpdate_buildFileModifiedThenReverted() {
    QueryState state =
        QueryState.builder()
            .queryOutput(
                new QuerySummary(
                    QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")))
            .includePaths(ImmutableList.of(Path.of("package")))
            .upstreamRevision("2")
            .workingSet(
                ImmutableSet.of(
                    new WorkspaceFileChange(Operation.MODIFY, Path.of("package/path/BUILD"))))
            .build();

    // new working set does not include deleted build file, so package is re-added
    AffectedPackages affected = state.deltaUpdate("2", ImmutableSet.of(), TEST_CONTEXT);
    assertThat(affected).isNotNull();
    assertThat(affected.getModifiedPackages()).containsExactly(Path.of("package/path"));
  }
}
