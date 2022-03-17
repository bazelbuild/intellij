/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.vcs;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Provides a diff against the version control system. */
public interface BlazeVcsHandler {
  ExtensionPointName<BlazeVcsHandler> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.VcsHandler");

  @Nullable
  static BlazeVcsHandler vcsHandlerForProject(Project project) {
    BuildSystemName buildSystemName = Blaze.getBuildSystemName(project);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    for (BlazeVcsHandler candidate : BlazeVcsHandler.EP_NAME.getExtensions()) {
      if (candidate.handlesProject(buildSystemName, workspaceRoot)) {
        return candidate;
      }
    }
    return null;
  }

  /** Returns the name of this VCS, eg. "git" or "hg" */
  String getVcsName();

  /** Returns whether this vcs handler can manage this project */
  boolean handlesProject(BuildSystemName buildSystemName, WorkspaceRoot workspaceRoot);

  /** Returns the working set of modified files compared to some "upstream". */
  ListenableFuture<WorkingSet> getWorkingSet(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ListeningExecutorService executor);

  /** Returns the original file content of a file path from "upstream". */
  ListenableFuture<String> getUpstreamContent(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      WorkspacePath path,
      ListeningExecutorService executor);

  /** Optionally creates a sync handler to perform vcs-specific computation during sync. */
  @Nullable
  BlazeVcsSyncHandler createSyncHandler(Project project, WorkspaceRoot workspaceRoot);

  /** Sync handler that performs VCS specific computation. */
  interface BlazeVcsSyncHandler {
    enum ValidationResult {
      OK,
      Error,
      RestartSync, // The sync process needs restarting
    }

    /**
     * Updates the vcs state of the project.
     *
     * @return True for OK, false to abort the sync process.
     */
    boolean update(BlazeContext context, ListeningExecutorService executor);

    /** Returns a custom workspace path resolver for this vcs. */
    @Nullable
    WorkspacePathResolver getWorkspacePathResolver();

    /** Validates the project view. Can cause sync to fail or restart. */
    ValidationResult validateProjectView(BlazeContext context, ProjectViewSet projectViewSet);
  }
}
