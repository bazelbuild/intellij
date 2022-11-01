/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Provides a {@link BlazeVcsHandler} for integration tests. */
public class MockBlazeVcsHandler implements BlazeVcsHandler {

  @Override
  public String getVcsName() {
    return "Mock";
  }

  @Override
  public boolean handlesProject(BuildSystemName buildSystemName, WorkspaceRoot workspaceRoot) {
    return true;
  }

  @Override
  public ListenableFuture<WorkingSet> getWorkingSet(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ListeningExecutorService executor) {
    WorkingSet workingSet =
        new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    return Futures.immediateFuture(workingSet);
  }

  @Override
  public ListenableFuture<String> getUpstreamContent(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      WorkspacePath path,
      ListeningExecutorService executor) {
    return Futures.immediateFuture("");
  }

  @Nullable
  @Override
  public BlazeVcsSyncHandler createSyncHandler(Project project, WorkspaceRoot workspaceRoot) {
    return null;
  }
}
