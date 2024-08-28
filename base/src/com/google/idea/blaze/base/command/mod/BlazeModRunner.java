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
package com.google.idea.blaze.base.command.mod;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.util.List;

/** Runs the {@code blaze mod ...} command. The results may be cached in the workspace. */
public abstract class BlazeModRunner {

  public static BlazeModRunner getInstance() {
    return ApplicationManager.getApplication().getService(BlazeModRunner.class);
  }

  /**
   * This calls {@code blaze mod dump_repo_mapping workspace} so blaze mod will return all mapped
   * repos visible to the current workspace.
   *
   * @param flags The blaze flags that will be passed to Blaze.
   * @return a ListenableFuture<ExternalWorkspaceData>
   */
  public abstract ListenableFuture<ExternalWorkspaceData> dumpRepoMapping(
      Project project,
      BuildInvoker invoker,
      BlazeContext context,
      BuildSystemName buildSystemName,
      List<String> flags);

  /**
   * @param args The arguments passed into `blaze mod ...`
   * @param flags The blaze flags that will be passed to {@code blaze ...}
   * @return the stdout bytes of the command
   */
  protected abstract ListenableFuture<byte[]> runBlazeModGetBytes(
      Project project,
      BuildInvoker invoker,
      BlazeContext context,
      List<String> args,
      List<String> flags);
}
