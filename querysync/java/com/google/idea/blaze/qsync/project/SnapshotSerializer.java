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
package com.google.idea.blaze.qsync.project;

import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange.Operation;
import com.google.protobuf.AbstractMessageLite;
import java.nio.file.Path;

/** Serializes a {@link PostQuerySyncData} instance to a proto message. */
public class SnapshotSerializer {

  static final ImmutableBiMap<Operation, SnapshotProto.WorkspaceFileChange.VcsOperation> OP_MAP =
      ImmutableBiMap.of(
          Operation.ADD, SnapshotProto.WorkspaceFileChange.VcsOperation.ADD,
          Operation.DELETE, SnapshotProto.WorkspaceFileChange.VcsOperation.DELETE,
          Operation.MODIFY, SnapshotProto.WorkspaceFileChange.VcsOperation.MODIFY);

  private final SnapshotProto.Snapshot.Builder proto;

  public SnapshotSerializer() {
    proto = SnapshotProto.Snapshot.newBuilder();
  }

  @CanIgnoreReturnValue
  public SnapshotSerializer visit(PostQuerySyncData snapshot) {
    visitProjectDefinition(snapshot.projectDefinition());
    snapshot.vcsState().ifPresent(this::visitVcsState);
    visitQuerySummary(snapshot.querySummary());
    return this;
  }

  public AbstractMessageLite<?, ?> toProto() {
    return proto.build();
  }

  private void visitProjectDefinition(ProjectDefinition projectDefinition) {
    SnapshotProto.ProjectDefinition.Builder proto = this.proto.getProjectDefinitionBuilder();
    projectDefinition.projectIncludes().stream()
        .map(Path::toString)
        .forEach(proto::addIncludePaths);
    projectDefinition.projectExcludes().stream()
        .map(Path::toString)
        .forEach(proto::addExcludePaths);
  }

  private void visitVcsState(VcsState vcsState) {
    SnapshotProto.VcsState.Builder vcsProto = proto.getVcsStateBuilder();
    vcsProto.setUpstreamRevision(vcsState.upstreamRevision);
    for (WorkspaceFileChange change : vcsState.workingSet) {
      vcsProto.addWorkingSet(
          SnapshotProto.WorkspaceFileChange.newBuilder()
              .setOperation(OP_MAP.get(change.operation))
              .setWorkspaceRelativePath(change.workspaceRelativePath.toString()));
    }
  }

  private void visitQuerySummary(QuerySummary summary) {
    proto.setQuerySummary(summary.proto());
  }
}
