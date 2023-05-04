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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import com.google.idea.blaze.qsync.query.Query;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/** Deserializes a {@link PostQuerySyncData} instance from an input stream. */
public class SnapshotDeserializer {

  private static final ImmutableBiMap<SnapshotProto.WorkspaceFileChange.VcsOperation, Operation>
      OP_MAP = SnapshotSerializer.OP_MAP.inverse();

  private final PostQuerySyncData.Builder snapshot;

  public SnapshotDeserializer() {
    snapshot = PostQuerySyncData.builder();
  }

  @CanIgnoreReturnValue
  public SnapshotDeserializer readFrom(InputStream in) throws IOException {
    SnapshotProto.Snapshot proto =
        SnapshotProto.Snapshot.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
    visitProjectDefinition(proto.getProjectDefinition());
    if (proto.hasVcsState()) {
      visitVcsState(proto.getVcsState());
    }
    visitQuerySummay(proto.getQuerySummary());
    return this;
  }

  public PostQuerySyncData getSyncData() {
    return snapshot.build();
  }

  private void visitProjectDefinition(SnapshotProto.ProjectDefinition proto) {
    snapshot.setProjectDefinition(
        ProjectDefinition.create(
            proto.getIncludePathsList().stream().map(Path::of).collect(toImmutableSet()),
            proto.getExcludePathsList().stream().map(Path::of).collect(toImmutableSet())));
  }

  private void visitVcsState(SnapshotProto.VcsState proto) {
    VcsState state =
        new VcsState(
            proto.getUpstreamRevision(),
            proto.getWorkingSetList().stream()
                .map(
                    c ->
                        new WorkspaceFileChange(
                            OP_MAP.get(c.getOperation()), Path.of(c.getWorkspaceRelativePath())))
                .collect(toImmutableSet()));
    snapshot.setVcsState(Optional.of(state));
  }

  private void visitQuerySummay(Query.Summary proto) {
    snapshot.setQuerySummary(proto);
  }
}
