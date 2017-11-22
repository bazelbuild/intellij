/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.BlazeConsolePopupBehavior;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.intellij.openapi.project.Project;
import java.util.List;

/**
 * Runs a blaze build command associated with a {@link BlazeCommandRunConfiguration configuration},
 * typically as a 'before run configuration' task.
 */
public final class BlazeBeforeRunCommandHelper {

  private BlazeBeforeRunCommandHelper() {}

  /** Kicks off the blaze build task, returning a corresponding {@link ListenableFuture}. */
  public static ListenableFuture<BuildResult> runBlazeBuild(
      BlazeCommandRunConfiguration configuration,
      BuildResultHelper buildResultHelper,
      List<String> extraBlazeFlags,
      String progressMessage) {
    Project project = configuration.getProject();
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath();

    BlazeConsolePopupBehavior consolePopupBehavior =
        BlazeUserSettings.getInstance().getSuppressConsoleForRunAction()
            ? BlazeConsolePopupBehavior.NEVER
            : BlazeConsolePopupBehavior.ALWAYS;

    return ProgressiveTaskWithProgressIndicator.builder(project)
        .submitTaskWithResult(
            new ScopedTask<BuildResult>() {
              @Override
              protected BuildResult execute(BlazeContext context) {
                context
                    .push(new IssuesScope(project))
                    .push(
                        new BlazeConsoleScope.Builder(project)
                            .setPopupBehavior(consolePopupBehavior)
                            .build());

                context.output(new StatusOutput(progressMessage));

                BlazeCommand.Builder command =
                    BlazeCommand.builder(binaryPath, BlazeCommandName.BUILD)
                        .addTargets(configuration.getTarget())
                        .addBlazeFlags(
                            BlazeFlags.blazeFlags(
                                project,
                                projectViewSet,
                                BlazeCommandName.BUILD,
                                BlazeInvocationContext.RunConfiguration))
                        .addBlazeFlags(handlerState.getBlazeFlagsState().getExpandedFlags())
                        .addBlazeFlags(extraBlazeFlags)
                        .addBlazeFlags(buildResultHelper.getBuildFlags());

                int exitCode =
                    ExternalTask.builder(workspaceRoot)
                        .addBlazeCommand(command.build())
                        .context(context)
                        .stderr(
                            buildResultHelper.stderr(
                                BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                    project, context, workspaceRoot)))
                        .build()
                        .run();
                return BuildResult.fromExitCode(exitCode);
              }
            });
  }
}
