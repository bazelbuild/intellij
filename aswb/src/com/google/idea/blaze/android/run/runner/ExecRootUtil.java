/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Utility for fetching execroot. */
public final class ExecRootUtil {
  /** Enables using blaze info as fallback for fetching execroot. */
  private static final BoolExperiment useBlazeInfoAsExecrootFallback =
      new BoolExperiment("enable.execroot.fallback", false);

  private static final Logger log = Logger.getInstance(ExecRootUtil.class);

  /**
   * Returns the execroot of the given project.
   *
   * <p>This method tries to get the execroot from BEP output. In the event where BEP isn't reliable
   * for obtaining the execroot, enabling {@link #useBlazeInfoAsExecrootFallback} will cause this
   * method to also obtain the execroot using blaze info.
   */
  @Nullable
  public static String getExecutionRoot(
      BuildResultHelper buildResultHelper,
      Project project,
      ImmutableList<String> buildFlags,
      BlazeContext context)
      throws GetArtifactsException {
    String executionRoot = buildResultHelper.getBuildOutput().getLocalExecRoot();
    if (executionRoot != null) {
      return executionRoot;
    } else if (!useBlazeInfoAsExecrootFallback.getValue()) {
      return null;
    }

    log.warn("Could not get execroot from BEP. Falling back to using blaze info.");
    context.output(new StatusOutput("Fetching project output directory..."));
    ListenableFuture<String> execRootFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                project,
                Blaze.getBuildSystemProvider(project)
                    .getBuildSystem()
                    .getDefaultInvoker(project, context),
                context,
                buildFlags,
                BlazeInfo.EXECUTION_ROOT_KEY);
    try {
      return execRootFuture.get();
    } catch (InterruptedException e) {
      IssueOutput.warn("Build cancelled.").submit(context);
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error(e.getMessage()).submit(context);
      context.setHasError();
    }
    return null;
  }

  private ExecRootUtil() {}
}
