/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android.run.runner;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfiguration;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.requiredAndroidModelMissing;
import static org.jetbrains.android.actions.RunAndroidAvdManagerAction.getName;

/**
 * Supports the entire run configuration flow. Used by both android_binary and android_test.
 *
 * Does any verification necessary, builds the APK and installs it, launches and debug tasks, etc.
 *
 * Any indirection between android_binary/android_test, mobile-install, InstantRun etc. should
 * come via the strategy class.
 */
public final class BlazeAndroidRunConfigurationRunner implements JDOMExternalizable {

  private static final Logger LOG = Logger.getInstance(BlazeAndroidRunConfigurationRunner.class);

  private static final String SYNC_FAILED_ERR_MSG = "Project state is invalid. Please sync and try your action again.";

  public static final Key<BlazeAndroidRunContext> RUN_CONTEXT_KEY = Key.create("blaze.run.context");
  public static final Key<BlazeAndroidDeviceSelector.DeviceSession> DEVICE_SESSION_KEY = Key.create("blaze.device.session");

  // We need to split "-c dbg" into two flags because we pass flags as a list of strings to the command line executor and we need blaze
  // to see -c and dbg as two separate entities, not one.
  private static final ImmutableList<String> NATIVE_DEBUG_FLAGS = ImmutableList.of("--fission=no", "-c", "dbg");

  private final Project project;

  private final BlazeAndroidRunConfiguration runConfiguration;

  private final BlazeAndroidRunConfigurationCommonState commonState;

  private final int runConfigId;

  private final BlazeAndroidRunConfigurationDeployTargetManager deployTargetManager;

  private final BlazeAndroidRunConfigurationDebuggerManager debuggerManager;

  public BlazeAndroidRunConfigurationRunner(Project project,
                                            BlazeAndroidRunConfiguration runConfiguration,
                                            BlazeAndroidRunConfigurationCommonState commonState,
                                            boolean isAndroidTest,
                                            int runConfigId) {
    this.project = project;
    this.runConfiguration = runConfiguration;
    this.commonState = commonState;
    this.runConfigId = runConfigId;
    this.deployTargetManager = new BlazeAndroidRunConfigurationDeployTargetManager(runConfigId, isAndroidTest);
    this.debuggerManager = new BlazeAndroidRunConfigurationDebuggerManager(project, commonState);
  }

  private ImmutableList<String> getBuildFlags(Project project, ProjectViewSet projectViewSet) {
    return ImmutableList.<String>builder()
      .addAll(BlazeFlags.buildFlags(project, projectViewSet))
      .addAll(commonState.getUserFlags())
      .addAll(getNativeDebuggerFlags())
      .build();
  }

  public ImmutableList<String> getNativeDebuggerFlags() {
    return commonState.isNativeDebuggingEnabled() ? NATIVE_DEBUG_FLAGS : ImmutableList.of();
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a warning.
   * We use a separate method for the collection so the compiler prevents us from accidentally throwing.
   */
  public List<ValidationError> validate(@Nullable Module module) {
    List<ValidationError> errors = Lists.newArrayList();
    if (module == null) {
      errors.add(ValidationError.fatal("No run configuration module found"));
      return errors;
    }

    if (commonState.getTarget() == null) {
      errors.add(ValidationError.fatal("No target selected"));
      return errors;
    }

    final Project project = module.getProject();
    if (requiredAndroidModelMissing(project)) {
      errors.add(ValidationError.fatal(SYNC_FAILED_ERR_MSG));
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      // Can't proceed.
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("no.facet.error", module.getName())));
    }

    if (facet.getConfiguration().getAndroidPlatform() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }

    errors.addAll(deployTargetManager.validate(facet));
    errors.addAll(debuggerManager.validate(facet));

    return errors;
  }

  @Nullable
  public final RunProfileState getState(Module module,
                                        final Executor executor,
                                        ExecutionEnvironment env) throws ExecutionException {

    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";
    Project project = env.getProject();

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      throw new ExecutionException("Could not load project view. Please resync project");
    }
    ImmutableList<String> buildFlags = getBuildFlags(project, projectViewSet);

    BlazeAndroidRunContext runContext = runConfiguration.createRunContext(
      project,
      facet,
      env,
      buildFlags
    );

    runContext.augmentEnvironment(env);

    boolean debug = executor instanceof  DefaultDebugExecutor;
    if (debug && !AndroidSdkUtils.activateDdmsIfNecessary(facet.getModule().getProject())) {
      throw new ExecutionException("Unable to obtain debug bridge. Please check if there is a different tool using adb that is active.");
    }

    AndroidSessionInfo info = AndroidSessionInfo.findOldSession(project, null, runConfigId);

    BlazeAndroidDeviceSelector deviceSelector = runContext.getDeviceSelector();
    BlazeAndroidDeviceSelector.DeviceSession deviceSession = deviceSelector.getDevice(
      project,
      facet,
      deployTargetManager,
      executor,
      env,
      info,
      debug,
      runConfigId
    );
    if (deviceSession == null) {
      return null;
    }

    DeployTarget deployTarget = deviceSession.deployTarget;
    if (deployTarget != null && deployTarget.hasCustomRunProfileState(executor)) {
      DeployTargetState deployTargetState = deployTargetManager.getCurrentDeployTargetState();
      return deployTarget.getRunProfileState(executor, env, deployTargetState);
    }

    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    if (deviceFutures == null) {
      // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
      return null;
    }

    if (deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    if (debug) {
      String error = canDebug(deviceFutures, facet, module.getName());
      if (error != null) {
        throw new ExecutionException(error);
      }
    }

    LaunchOptions.Builder launchOptionsBuilder = getDefaultLaunchOptions()
      .setDebug(debug);
    runContext.augmentLaunchOptions(launchOptionsBuilder);
    LaunchOptions launchOptions = launchOptionsBuilder.build();

    // Store the run context on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(RUN_CONTEXT_KEY, runContext);
    env.putCopyableUserData(DEVICE_SESSION_KEY, deviceSession);

    return new BlazeAndroidRunState(
      module,
      env,
      getName(),
      launchOptions,
      deviceSession,
      runContext
    );
  }

  private static String canDebug(DeviceFutures deviceFutures, AndroidFacet facet, String moduleName) {
    // If we are debugging on a device, then the app needs to be debuggable
    for (ListenableFuture<IDevice> future : deviceFutures.get()) {
      if (!future.isDone()) {
        // this is an emulator, and we assume that all emulators are debuggable
        continue;
      }
      IDevice device = Futures.getUnchecked(future);
      if (!LaunchUtils.canDebugAppOnDevice(facet, device)) {
        return AndroidBundle.message("android.cannot.debug.noDebugPermissions", moduleName, device.getName());
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

  public boolean executeBuild(ExecutionEnvironment env) {
    boolean suppressConsole = BlazeUserSettings.getInstance().getSuppressConsoleForRunAction();
    return Scope.root(context -> {
      context
        .push(new IssuesScope(project))
        .push(new ExperimentScope())
        .push(new BlazeConsoleScope.Builder(project).setSuppressConsole(suppressConsole).build())
        .push(new LoggedTimingScope(project, Action.APK_BUILD_AND_INSTALL))
      ;

      BlazeAndroidRunContext runContext = env.getCopyableUserData(RUN_CONTEXT_KEY);
      if (runContext == null) {
        IssueOutput.error("Could not find run context. Please try again").submit(context);
        return false;
      }
      BlazeAndroidDeviceSelector.DeviceSession deviceSession = env.getCopyableUserData(DEVICE_SESSION_KEY);

      BlazeApkBuildStep buildStep = runContext.getBuildStep();
      try {
        return buildStep.build(context, deviceSession);
      } catch (Exception e) {
        LOG.error(e);
        return false;
      }
    });
  }

  private final class BlazeAndroidRunState implements RunProfileState {

    private final Module module;
    private final ExecutionEnvironment env;
    private final String launchConfigName;
    private final BlazeAndroidDeviceSelector.DeviceSession deviceSession;
    private final BlazeAndroidRunContext runContext;
    private final LaunchOptions launchOptions;

    private BlazeAndroidRunState(Module module,
                                 ExecutionEnvironment env,
                                 String launchConfigName,
                                 LaunchOptions launchOptions,
                                 BlazeAndroidDeviceSelector.DeviceSession deviceSession,
                                 BlazeAndroidRunContext runContext) {
      this.module = module;
      this.env = env;
      this.launchConfigName = launchConfigName;
      this.deviceSession = deviceSession;
      this.runContext = runContext;
      this.launchOptions = launchOptions;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      ProcessHandler processHandler;
      ConsoleView console;

      ApplicationIdProvider applicationIdProvider = runContext.getApplicationIdProvider();

      String applicationId;
      try {
        applicationId = applicationIdProvider.getPackageName();
      }
      catch (ApkProvisionException e) {
        throw new ExecutionException("Unable to obtain application id", e);
      }

      LaunchTasksProvider launchTasksProvider = runContext.getLaunchTasksProvider(launchOptions, debuggerManager);

      DeviceFutures deviceFutures = deviceSession.deviceFutures;
      assert deviceFutures != null;
      ProcessHandler previousSessionProcessHandler = deviceSession.sessionInfo != null ?
                                                     deviceSession.sessionInfo.getProcessHandler() : null;

      if (launchTasksProvider.createsNewProcess()) {
        // In the case of cold swap, there is an existing process that is connected, but we are going to launch a new one.
        // Detach the previous process handler so that we don't end up with 2 run tabs for the same launch (the existing one
        // and the new one).
        if (previousSessionProcessHandler != null) {
          previousSessionProcessHandler.detachProcess();
        }

        processHandler = new AndroidProcessHandler(applicationId, launchTasksProvider.monitorRemoteProcess());
        console = runContext.getConsoleProvider().createAndAttach(module.getProject(), processHandler, executor);
      } else {
        assert previousSessionProcessHandler != null : "No process handler from previous session, yet current tasks don't create one";
        processHandler = previousSessionProcessHandler;
        console = null;
      }

      LaunchInfo launchInfo = new LaunchInfo(executor, runner, env, runContext.getConsoleProvider());

      LaunchTaskRunner task = new LaunchTaskRunner(module.getProject(),
                                                   launchConfigName,
                                                   launchInfo,
                                                   processHandler,
                                                   deviceSession.deviceFutures,
                                                   launchTasksProvider);
      ProgressManager.getInstance().run(task);

      return console == null ? null : new DefaultExecutionResult(console, processHandler);
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    deployTargetManager.readExternal(element);
    debuggerManager.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    deployTargetManager.writeExternal(element);
    debuggerManager.writeExternal(element);
  }
}
