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

package com.google.idea.blaze.android.run.runner;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.run.BlazeAndroidRunState;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;

/**
 * Supports the execution. Used by both android_binary and android_test.
 *
 * <p>Builds the APK and installs it, launches and debug tasks, etc.
 *
 * <p>Any indirection between android_binary/android_test, mobile-install, InstantRun etc. should
 * come via the strategy class.
 */
public final class BlazeAndroidRunConfigurationRunner
    implements BlazeCommandRunConfigurationRunner {

  private static final Logger LOG = Logger.getInstance(BlazeAndroidRunConfigurationRunner.class);

  private static final Key<BlazeAndroidRunContext> RUN_CONTEXT_KEY =
      Key.create("blaze.run.context");
  public static final Key<BlazeAndroidDeviceSelector.DeviceSession> DEVICE_SESSION_KEY =
      Key.create("blaze.device.session");

  private final Module module;
  private final BlazeAndroidRunContext runContext;
  private final BlazeCommandRunConfiguration runConfig;

  public BlazeAndroidRunConfigurationRunner(
      Module module, BlazeAndroidRunContext runContext, BlazeCommandRunConfiguration runConfig) {
    this.module = module;
    this.runContext = runContext;
    this.runConfig = runConfig;
  }

  @Override
  @Nullable
  public final RunProfileState getRunProfileState(final Executor executor, ExecutionEnvironment env)
      throws ExecutionException {

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in createRunner.";
    final Project project = env.getProject();

    boolean isDebug = executor instanceof DefaultDebugExecutor;
    AndroidSessionInfo info =
        AndroidSessionInfo.findOldSession(project, null, runConfig, env.getExecutionTarget());

    BlazeAndroidDeviceSelector deviceSelector = runContext.getDeviceSelector();
    BlazeAndroidDeviceSelector.DeviceSession deviceSession =
        deviceSelector.getDevice(
            project, facet, executor, env, info, isDebug, runConfig.getUniqueID());
    if (deviceSession == null) {
      return null;
    }

    DeployTarget deployTarget = deviceSession.deployTarget;
    if (deployTarget != null && deployTarget.hasCustomRunProfileState(executor)) {
      return DeployTargetCompat.getRunProfileState(deployTarget, executor, env);
    }

    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    if (deviceFutures == null) {
      // The user deliberately canceled, or some error was encountered and exposed by the chooser.
      // Quietly exit.
      return null;
    }

    if (deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    if (isDebug) {
      String error = canDebug(deviceFutures, facet, module.getName());
      if (error != null) {
        throw new ExecutionException(error);
      }
    }

    LaunchOptions.Builder launchOptionsBuilder = getDefaultLaunchOptions().setDebug(isDebug);
    runContext.augmentLaunchOptions(launchOptionsBuilder);

    // Store the run context on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(RUN_CONTEXT_KEY, runContext);
    env.putCopyableUserData(DEVICE_SESSION_KEY, deviceSession);

    return new BlazeAndroidRunState(env, launchOptionsBuilder, deviceSession, runContext);
  }

  private static String canDebug(
      DeviceFutures deviceFutures, AndroidFacet facet, String moduleName) {
    // If we are debugging on a device, then the app needs to be debuggable
    for (ListenableFuture<IDevice> future : deviceFutures.get()) {
      if (!future.isDone()) {
        // this is an emulator, and we assume that all emulators are debuggable
        continue;
      }
      IDevice device = Futures.getUnchecked(future);
      if (!LaunchUtils.canDebugAppOnDevice(facet, device)) {
        return AndroidBundle.message(
            "android.cannot.debug.noDebugPermissions", moduleName, device.getName());
      }
    }
    return null;
  }

  private static LaunchOptions.Builder getDefaultLaunchOptions() {
    return LaunchOptions.builder()
        .setClearLogcatBeforeStart(false)
        .setSkipNoopApkInstallations(true)
        .setForceStopRunningApp(true);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    final Project project = env.getProject();
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    return Scope.root(
        context -> {
          context
              .push(new ProblemsViewScope(project, settings.getShowProblemsViewOnRun()))
              .push(new ExperimentScope())
              .push(
                  new BlazeConsoleScope.Builder(project)
                      .setPopupBehavior(settings.getShowBlazeConsoleOnRun())
                      .addConsoleFilters(
                          new IssueOutputFilter(
                              project,
                              WorkspaceRoot.fromProject(project),
                              BlazeInvocationContext.ContextType.RunConfiguration,
                              true))
                      .build())
              .push(new IdeaLogScope());

          BlazeAndroidRunContext runContext = env.getCopyableUserData(RUN_CONTEXT_KEY);
          if (runContext == null) {
            IssueOutput.error("Could not find run context. Please try again").submit(context);
            return false;
          }
          BlazeAndroidDeviceSelector.DeviceSession deviceSession =
              env.getCopyableUserData(DEVICE_SESSION_KEY);

          BlazeApkBuildStep buildStep = runContext.getBuildStep();
          ScopedTask<Void> buildTask =
              new ScopedTask<Void>(context) {
                @Override
                protected Void execute(BlazeContext context) {
                  buildStep.build(context, deviceSession);
                  return null;
                }
              };

          try {
            ListenableFuture<Void> buildFuture =
                ProgressiveTaskWithProgressIndicator.builder(
                        project,
                        String.format("Executing %s apk build", Blaze.buildSystemName(project)))
                    .submitTaskWithResult(buildTask);
            Futures.getChecked(buildFuture, ExecutionException.class);
          } catch (ExecutionException e) {
            context.setHasError();
          } catch (CancellationException e) {
            context.setCancelled();
          } catch (Exception e) {
            LOG.error(e);
            return false;
          }
          return context.shouldContinue();
        });
  }
}
