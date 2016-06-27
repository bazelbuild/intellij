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
package com.google.idea.blaze.base.vcs.git;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Vcs diff provider for git
 */
public class GitBlazeVcsHandler implements BlazeVcsHandler {

  private static final Logger LOG = Logger.getInstance(GitBlazeVcsHandler.class);

  @Nullable
  @Override
  public String getClientName(WorkspaceRoot workspaceRoot) {
    return null;
  }

  @Override
  public boolean handlesProject(Project project, WorkspaceRoot workspaceRoot) {
    return Blaze.getBuildSystem(project) == BuildSystem.Bazel
      && isGitRepository(workspaceRoot)
      && tracksRemote(workspaceRoot);
  }

  @Override
  public ListenableFuture<WorkingSet> getWorkingSet(Project project,
                                                    WorkspaceRoot workspaceRoot,
                                                    ListeningExecutorService executor) {
    return executor.submit(() -> {
      String upstreamSha = getUpstreamSha(workspaceRoot, false);
      if (upstreamSha == null) {
        return null;
      }
      return GitDiffProvider.calculateDiff(workspaceRoot, upstreamSha);
    });
  }

  @Nullable
  @Override
  public BlazeVcsSyncHandler createSyncHandler(Project project,
                                               WorkspaceRoot workspaceRoot) {
    return null;
  }

  private static boolean isGitRepository(WorkspaceRoot workspaceRoot) {
    // TODO: What if the git repo root is a parent directory of the workspace root?
    // Just call 'git rev-parse --is-inside-work-tree' or similar instead?
    File gitDir = new File(workspaceRoot.directory(), ".git");
    return FileAttributeProvider.getInstance().isDirectory(gitDir);
  }

  /**
   * If we're not on a git branch which tracks a remote, we have no way of determining a WorkingSet.
   */
  private static boolean tracksRemote(WorkspaceRoot workspaceRoot) {
    return getUpstreamSha(workspaceRoot, true) != null;
  }

  /**
   * Returns the git commit SHA corresponding to the most recent commit
   * in the current branch which matches a commit in the currently-tracked remote branch.
   */
  @Nullable
  public static String getUpstreamSha(WorkspaceRoot workspaceRoot, boolean suppressErrors) {
    return getConsoleOutput(workspaceRoot, ImmutableList.of("git", "rev-parse", "@{u}"), suppressErrors);
  }

  /**
   * @return the console output, in string form, or null if there was a non-zero exit code.
   */
  @Nullable
  private static String getConsoleOutput(WorkspaceRoot workspaceRoot, ImmutableList<String> command, boolean suppressErrors) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int retVal = ExternalTask.builder(workspaceRoot, command)
      .stdout(stdout)
      .stderr(stderr)
      .build()
      .run();
    if (retVal != 0) {
      if (!suppressErrors) {
        LOG.error(stderr);
      }
      return null;
    }
    return StringUtil.trimEnd(stdout.toString(), "\n");
  }

}
