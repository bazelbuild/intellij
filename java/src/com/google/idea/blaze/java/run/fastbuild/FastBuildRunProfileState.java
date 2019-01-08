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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.run.BlazeJavaDebuggableRunProfileState;
import com.google.idea.blaze.java.run.BlazeJavaRunConfigState;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaCommandLineStateUtil;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

final class FastBuildRunProfileState extends BlazeJavaDebuggableRunProfileState {

  FastBuildRunProfileState(ExecutionEnvironment env) {
    super(env);
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    File outputFile = createOutputFile();
    Project project = getConfiguration().getProject();
    Label label = getLabel();
    BlazeTestUiSession testUiSession =
        BlazeTestUiSession.create(
            /* blazeFlags= */ ImmutableList.of(),
            new FastBuildTestResultFinderStrategy(
                label, getConfiguration().getTargetKind(), outputFile, getBlazeContext()));
    setConsoleBuilder(
        new TextConsoleBuilderImpl(project) {
          @Override
          protected ConsoleView createConsole() {
            return SmRunnerUtils.getConsoleView(
                project, getConfiguration(), getEnvironment().getExecutor(), testUiSession);
          }
        });

    BlazeJavaRunConfigState handlerState =
        (BlazeJavaRunConfigState) getConfiguration().getHandler().getState();

    int debugPort = -1;
    if (getExecutorType().isDebugType()) {
      debugPort = handlerState.getDebugPortState().port;
    }

    FastBuildTestEnvironmentCreator testEnvironmentCreator =
        FastBuildTestEnvironmentCreatorFactory.getInstance(Blaze.getBuildSystem(project))
            .getTestEnvironmentCreator(project);
    return JavaCommandLineStateUtil.startProcess(
        testEnvironmentCreator.createCommandLine(
            getConfiguration().getTargetKind(),
            getFastBuildInfo(),
            outputFile,
            handlerState.getTestFilter(),
            debugPort));
  }

  private File createOutputFile() throws ExecutionException {
    try {
      File outputXmlFile = File.createTempFile("ide-fast-build-test-output-", ".xml");
      outputXmlFile.deleteOnExit();
      return outputXmlFile;
    } catch (IOException e) {
      throw new ExecutionException("Error creating test output XML file.", e);
    }
  }

  private Label getLabel() throws ExecutionException {
    if (!(getConfiguration().getTarget() instanceof Label)) {
      // We shouldn't ever have this problem since we checked FastBuildService#supportsFastBuild
      // and that will return false for wildcard targets.
      throw new ExecutionException("Fast builds only operate on single targets, not wildcards.");
    }
    return (Label) getConfiguration().getTarget();
  }

  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner runner)
      throws ExecutionException {
    DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
    AbstractRerunFailedTestsAction rerunFailedAction =
        SmRunnerUtils.createRerunFailedTestsAction(result);
    result.setRestartActions(
        rerunFailedAction,
        new RerunFastBuildConfigurationWithBlazeAction(
            getConfiguration().getProject(), getFastBuildInfo().label(), getEnvironment()));
    return result;
  }

  private FastBuildInfo getFastBuildInfo() throws ExecutionException {
    AtomicReference<FastBuildInfo> userData =
        getEnvironment().getCopyableUserData(FastBuildConfigurationRunner.BUILD_INFO_KEY);
    if (userData == null || userData.get() == null) {
      throw new ExecutionException(
          "No deploy jar stored in environment; before-run tasks weren't executed?");
    }
    return userData.get();
  }

  private BlazeContext getBlazeContext() throws ExecutionException {
    AtomicReference<BlazeContext> userData =
        getEnvironment().getCopyableUserData(FastBuildConfigurationRunner.BLAZE_CONTEXT);
    if (userData == null || userData.get() == null) {
      throw new ExecutionException(
          "No logging data stored in environment; before-run tasks weren't executed?");
    }
    return userData.get();
  }
}
