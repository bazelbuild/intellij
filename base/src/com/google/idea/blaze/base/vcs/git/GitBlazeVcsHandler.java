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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import javax.annotation.Nullable;

/** Vcs diff provider for git */
public class GitBlazeVcsHandler implements BlazeVcsHandler {

  private static final Logger logger = Logger.getInstance(GitBlazeVcsHandler.class);

  @Override
  public String getVcsName() {
    return "git";
  }

  @Override
  public boolean handlesProject(BuildSystemName buildSystemName, WorkspaceRoot workspaceRoot) {
    return buildSystemName == BuildSystemName.Bazel
        && isGitRepository(workspaceRoot)
        && tracksRemote(workspaceRoot);
  }

  @Override
  public ListenableFuture<WorkingSet> getWorkingSet(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ListeningExecutorService executor) {
    return executor.submit(
        () -> {
          String upstreamSha = getUpstreamSha(workspaceRoot, false);
          if (upstreamSha == null) {
            return null;
          }
          return GitWorkingSetProvider.calculateWorkingSet(workspaceRoot, upstreamSha, context);
        });
  }

  @Nullable
  @Override
  public BlazeVcsSyncHandler createSyncHandler(Project project, WorkspaceRoot workspaceRoot) {
    return null;
  }

  @Override
  public ListenableFuture<String> getUpstreamContent(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      WorkspacePath path,
      ListeningExecutorService executor) {
    return executor.submit(() -> getGitUpstreamContent(workspaceRoot, path));
  }

  private static String getGitUpstreamContent(WorkspaceRoot workspaceRoot, WorkspacePath path) {
    String upstreamSha = getUpstreamSha(workspaceRoot, false);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ExternalTask.builder(workspaceRoot)
        .args(
            "git",
            "show",
            // Normally "For plain blobs, it shows the plain contents.", but let's add some
            // options to be a bit more paranoid.
            "--no-color",
            "--no-expand-tabs",
            "--no-notes",
            "--no-textconv",
            String.format("%s:./%s", upstreamSha, path.relativePath()))
        .stdout(outputStream)
        .build()
        .run();
    return outputStream.toString();
  }

  private static boolean isGitRepository(WorkspaceRoot workspaceRoot) {
    // TODO: What if the git repo root is a parent directory of the workspace root?
    // Just call 'git rev-parse --is-inside-work-tree' or similar instead?
    File gitDir = new File(workspaceRoot.directory(), ".git");
    return FileOperationProvider.getInstance().isDirectory(gitDir);
  }

  /**
   * If we're not on a git branch which tracks a remote, we have no way of determining a WorkingSet.
   */
  private static boolean tracksRemote(WorkspaceRoot workspaceRoot) {
    return getUpstreamSha(workspaceRoot, true) != null;
  }

  /**
   * Returns the git commit SHA corresponding to the most recent commit in the current branch which
   * matches a commit in the currently-tracked remote branch.
   */
  @Nullable
  public static String getUpstreamSha(WorkspaceRoot workspaceRoot, boolean suppressErrors) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int retVal =
        ExternalTask.builder(workspaceRoot)
            .args("git", "rev-parse", "@{u}")
            .stdout(stdout)
            .stderr(stderr)
            .build()
            .run();
    if (retVal != 0) {
      if (!suppressErrors) {
        logger.error(stderr);
      }
      return null;
    }
    return StringUtil.trimEnd(stdout.toString(), "\n");
  }
}
