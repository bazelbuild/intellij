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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.qsync.query.WorkspaceFileChange;
import com.google.idea.blaze.qsync.query.WorkspaceFileChange.Operation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Listens for changes to files in the users workspace and notifies {@link QuerySyncManager} when
 * the occur.
 */
public class QuerySyncFileListener implements AsyncFileListener {

  class Applier implements ChangeApplier {

    private final ImmutableList<WorkspaceFileChange> changes;

    public Applier(ImmutableList<WorkspaceFileChange> changes) {
      this.changes = changes;
    }

    @Override
    public void afterVfsChange() {
      syncManager.handleFileSystemChange(changes);
    }
  }

  private static final Logger logger = Logger.getInstance(QuerySyncFileListener.class);

  private final Path workspaceRoot;
  private final QuerySyncManager syncManager;

  public QuerySyncFileListener(Project project, QuerySyncManager syncManager) {
    this.workspaceRoot = WorkspaceRoot.fromProject(project).path();
    this.syncManager = syncManager;
  }

  @Override
  @Nullable
  public ChangeApplier prepareChange(List<? extends VFileEvent> list) {
    ImmutableList.Builder<WorkspaceFileChange> changesBuilder = ImmutableList.builder();
    for (VFileEvent event : list) {
      Path absolutePath = Path.of(event.getPath());
      if (absolutePath.startsWith(workspaceRoot)) {
        WorkspaceFileChange.Builder change =
            WorkspaceFileChange.builder()
                .workspaceRelativePath(workspaceRoot.relativize(absolutePath));

        if (event instanceof VFileDeleteEvent) {
          change.operation(Operation.DELETE);
        } else if (event instanceof VFileCreateEvent) {
          change.operation(Operation.ADD);
        } else if (event instanceof VFileContentChangeEvent) {
          change.operation(Operation.MODIFY);
        } else {
          logger.warn(String.format("Warning: ignoring event %s", event));
          continue;
        }
        changesBuilder.add(change.build());
      }
    }
    ImmutableList<WorkspaceFileChange> changes = changesBuilder.build();
    if (!changes.isEmpty()) {
      logger.info(String.format("Processing changes for %d files", changes.size()));
      return new Applier(changes);
    }
    return null;
  }
}
