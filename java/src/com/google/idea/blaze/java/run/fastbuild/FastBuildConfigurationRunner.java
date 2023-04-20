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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.base.Preconditions.checkState;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.java.fastbuild.FastBuildException;
import com.google.idea.blaze.java.fastbuild.FastBuildException.BlazeBuildError;
import com.google.idea.blaze.java.fastbuild.FastBuildIncrementalCompileException;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildLogDataScope;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Supports the execution of {@link BlazeCommandRunConfiguration}s in fast build mode. */
public final class FastBuildConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  private static final Logger logger = Logger.getInstance(FastBuildConfigurationRunner.class);

  static final Key<AtomicReference<FastBuildInfo>> BUILD_INFO_KEY =
      Key.create("blaze.java.fastRun.buildInfo");
  static final Key<AtomicReference<BlazeContext>> BLAZE_CONTEXT =
      Key.create("blaze.java.fastRun.blazeContext");

  /** Returns false if this isn't a 'blaze test' invocation. */
  static boolean canRun(RunProfile runProfile) {
    BlazeCommandRunConfiguration blazeCfg =
        BlazeCommandRunConfigurationRunner.getBlazeConfig(runProfile);
    if (blazeCfg == null) {
      return false;
    }
    return Objects.equals(blazeCfg.getHandler().getCommandName(), BlazeCommandName.TEST)
        && FastBuildService.getInstance(blazeCfg.getProject())
            .supportsFastBuilds(
                Blaze.getBuildSystemName(blazeCfg.getProject()), blazeCfg.getTargetKind());
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    if (!canRun(env.getRunProfile())) {
      return new BlazeCommandRunProfileState(env);
    }
    FastBuildSuggestion.getInstance().triedFastBuild();
    env.putCopyableUserData(BUILD_INFO_KEY, new AtomicReference<>());
    env.putCopyableUserData(BLAZE_CONTEXT, new AtomicReference<>());
    return new FastBuildRunProfileState(env);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    if (!canRun(env.getRunProfile())) {
      return true;
    }
    Project project = env.getProject();
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getBlazeConfig(env.getRunProfile());
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState)
            Objects.requireNonNull(configuration).getHandler().getState();

    checkState(configuration.getSingleTarget() != null);
    Label label = (Label) configuration.getSingleTarget();

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath(project);

    SaveUtil.saveAllFiles();
    FastBuildService buildService = FastBuildService.getInstance(project);
    Future<FastBuildInfo> buildFuture = null;

    FocusBehavior consolePopupBehavior = BlazeUserSettings.getInstance().getShowBlazeConsoleOnRun();
    FocusBehavior problemsViewFocus = BlazeUserSettings.getInstance().getShowProblemsViewOnRun();
    BlazeContext context =
        BlazeContext.create()
            .push(
                new ToolWindowScope.Builder(
                        project,
                        new Task(project, "Fast Build " + label.targetName(), Task.Type.FAST_BUILD))
                    .setPopupBehavior(consolePopupBehavior)
                    .setIssueParsers(
                        BlazeIssueParser.defaultIssueParsers(
                            project,
                            WorkspaceRoot.fromProject(project),
                            ContextType.RunConfiguration))
                    .build())
            .push(new ProblemsViewScope(project, problemsViewFocus))
            .push(new IdeaLogScope())
            .push(new FastBuildLogDataScope());

    try {
      buildFuture =
          buildService.createBuild(
              context,
              label,
              binaryPath,
              handlerState.getBlazeFlagsState().getFlagsForExternalProcesses());
      FastBuildInfo fastBuildInfo = buildFuture.get();
      env.getCopyableUserData(BUILD_INFO_KEY).set(fastBuildInfo);
      env.getCopyableUserData(BLAZE_CONTEXT).set(context);
      return true;
    } catch (InterruptedException e) {
      cancelBuildFuture(buildFuture);
    } catch (CancellationException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(),
          env.getExecutor().getToolWindowId(),
          env.getRunProfile(),
          new RunCanceledByUserException());
    } catch (FastBuildException e) {
      if (!(e instanceof BlazeBuildError)) {
        // no need to log blaze build errors; they're expected
        logger.warn(e);
      }
      ExecutionUtil.handleExecutionError(env, new ExecutionException(e));
    } catch (java.util.concurrent.ExecutionException e) {
      logger.warn(e);
      if (e.getCause() instanceof FastBuildIncrementalCompileException) {
        handleJavacError(
            env,
            project,
            label,
            buildService,
            (FastBuildIncrementalCompileException) e.getCause(),
            context);
      } else {
        ExecutionUtil.handleExecutionError(env, new ExecutionException(e.getCause()));
      }
    }
    // Fall-through for all exceptions. If no exception was thrown, we return from the try{} block.
    context.close();
    return false;
  }

  @SuppressWarnings("Interruption") // propagating a cancellation-with-interrupt from another caller
  private void cancelBuildFuture(Future<FastBuildInfo> buildFuture) {
    buildFuture.cancel(/* mayInterruptIfRunning= */ true);
    Thread.currentThread().interrupt();
  }

  private static void handleJavacError(
      ExecutionEnvironment env,
      Project project,
      Label label,
      FastBuildService buildService,
      FastBuildIncrementalCompileException e,
      BlazeContext context) {

    context.output(IssueOutput.error(e.getMessage() + "\n").build());

    // TODO(b/240126599): Consider supporting outputting Hyperlinks to the Blaze console again.
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Fastbuild failed notification")
        .createNotification(
            "To run the tests again with a fresh "
                + Blaze.getBuildSystemName(project)
                + " build, click",
            NotificationType.ERROR)
        .addAction(
            NotificationAction.createExpiring(
                "here", (event, notification) -> rerunTests(env, label, buildService)))
        .notify(project);

    ExecutionUtil.handleExecutionError(
        env, new ExecutionException("See the Blaze Console for javac output", e.getCause()));
  }

  private static void rerunTests(
      ExecutionEnvironment env, Label label, FastBuildService buildService) {
    buildService.resetBuild(label);
    ExecutionUtil.restart(env);
    EventLoggingService.getInstance()
        .logEvent(FastBuildConfigurationRunner.class, "rerun_tests_with_blaze_link_clicked");
  }
}
