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
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.emptyProjectBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil;
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProjectRefresherTest {

  private ProjectRefresher createRefresher() {
    return new ProjectRefresher(QuerySyncTestUtils.EMPTY_PACKAGE_READER);
  }

  @Test
  public void testCreateRefreshQueryStrategy_upstreamRevisionChange() {
    BlazeProjectSnapshot project =
        emptyProjectBuilder().vcsState(Optional.of(new VcsState("1", ImmutableSet.of()))).build();

    ProjectUpdate update =
        createRefresher()
            .startPartialUpdate(
                QuerySyncTestUtils.NOOP_CONTEXT,
                project,
                Optional.of(new VcsState("2", ImmutableSet.of())));
    assertThat(update).isInstanceOf(FullProjectUpdate.class);
  }

  @Test
  public void testCreateRefreshQueryStrategy_buildFileAddedThenReverted() {
    BlazeProjectSnapshot project =
        emptyProjectBuilder()
            .queryOutput(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .vcsState(
                Optional.of(
                    new VcsState(
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.ADD, Path.of("package/path/BUILD"))))))
            .projectIncludes(ImmutableList.of(Path.of("package")))
            .build();

    ProjectUpdate update =
        createRefresher()
            .startPartialUpdate(
                QuerySyncTestUtils.NOOP_CONTEXT,
                project,
                Optional.of(new VcsState("1", ImmutableSet.of())));

    assertThat(update).isInstanceOf(PartialProjectUpdate.class);
    PartialProjectUpdate partialQuery = (PartialProjectUpdate) update;
    assertThat(partialQuery.deletedPackages).containsExactly(Path.of("package/path"));
    assertThat(partialQuery.modifiedPackages).isEmpty();
  }

  @Test
  public void testCreateRefreshQueryStrategy_buildFileDeletedThenReverted() {
    BlazeProjectSnapshot project =
        emptyProjectBuilder()
            .vcsState(
                Optional.of(
                    new VcsState(
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.DELETE, Path.of("package/path/BUILD"))))))
            .projectIncludes(ImmutableList.of(Path.of("package")))
            .build();

    ProjectUpdate update =
        createRefresher()
            .startPartialUpdate(
                QuerySyncTestUtils.NOOP_CONTEXT,
                project,
                Optional.of(new VcsState("1", ImmutableSet.of())));

    assertThat(update).isInstanceOf(PartialProjectUpdate.class);
    PartialProjectUpdate partialQuery = (PartialProjectUpdate) update;
    assertThat(partialQuery.deletedPackages).isEmpty();
    assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"));
  }

  @Test
  public void testCreateRefreshQueryStrategy_buildFileModifiedThenReverted() {
    BlazeProjectSnapshot project =
        emptyProjectBuilder()
            .queryOutput(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .vcsState(
                Optional.of(
                    new VcsState(
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.MODIFY, Path.of("package/path/BUILD"))))))
            .projectIncludes(ImmutableList.of(Path.of("package")))
            .build();

    ProjectUpdate update =
        createRefresher()
            .startPartialUpdate(
                QuerySyncTestUtils.NOOP_CONTEXT,
                project,
                Optional.of(new VcsState("1", ImmutableSet.of())));

    assertThat(update).isInstanceOf(PartialProjectUpdate.class);
    PartialProjectUpdate partialQuery = (PartialProjectUpdate) update;
    assertThat(partialQuery.deletedPackages).isEmpty();
    assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"));
  }
}
