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
package com.google.idea.blaze.android.run.test;

import static com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxAction.DEPLOYS_TO_LOCAL_DEVICE;

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_test targets.
 */
public class BlazeAndroidTestRunConfigurationHandler
    implements BlazeAndroidRunConfigurationHandler {

  private final BlazeCommandRunConfiguration configuration;
  private final BlazeAndroidTestRunConfigurationState configState;

  BlazeAndroidTestRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
    configState =
        new BlazeAndroidTestRunConfigurationState(
            Blaze.buildSystemName(configuration.getProject()));
    configuration.putUserData(DEPLOYS_TO_LOCAL_DEVICE, true);
  }

  @Override
  public BlazeAndroidTestRunConfigurationState getState() {
    return configState;
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return configState.getCommonState();
  }

  @Override
  @Nullable
  public Label getLabel() {
    TargetExpression target = configuration.getSingleTarget();
    if (target instanceof Label) {
      return (Label) target;
    }
    return null;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment env) throws ExecutionException {
    Project project = env.getProject();

    // This is a workaround for b/134587683
    // Due to the way blaze run configuration editors update the underlying configuration state,
    // it's possible for the configuration referenced in this handler to be out of date. This can
    // cause tricky side-effects such as incorrect build target and target validation settings.
    // Fortunately, the only field that can come out of sync is the target label and it's target
    // kind. The handlers are designed to only handle their supported target kinds, so we can
    // safely ignore all fields other than target label itself and extract an up to date target
    // label from the execution environment.
    // Validation of the updated target label is not needed here because:
    // 1. The target kind is guaranteed to be an android binary kind or else this
    //    specific handler will not be used.
    // 2. Any other validation is done during edit-time of the run configuration before saving.
    BlazeCommandRunConfiguration configFromEnv =
        BlazeAndroidRunConfigurationHandler.getCommandConfig(env);
    configuration.setTarget(configFromEnv.getSingleTarget());

    Module module =
        ModuleFinder.getInstance(env.getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeAndroidRunConfigurationValidationUtil.validateExecution(module, projectViewSet);

    ImmutableList<String> blazeFlags =
        configState
            .getCommonState()
            .getExpandedBuildFlags(
                project,
                projectViewSet,
                BlazeCommandName.TEST,
                BlazeInvocationContext.runConfigContext(
                    ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), false));
    ImmutableList<String> exeFlags =
        ImmutableList.copyOf(
            configState.getCommonState().getExeFlagsState().getFlagsForExternalProcesses());
    BlazeAndroidRunContext runContext = createRunContext(project, facet, env, blazeFlags, exeFlags);

    EventLoggingService.getInstance()
        .logEvent(
            BlazeAndroidTestRunConfigurationHandler.class,
            "BlazeAndroidTestRun",
            ImmutableMap.of(
                "launchMethod",
                configState.getLaunchMethod().name(),
                "executorId",
                env.getExecutor().getId()));

    return new BlazeAndroidRunConfigurationRunner(
        module,
        runContext,
        getCommonState().getDebuggerManager(),
        configuration);
  }

  private BlazeAndroidRunContext createRunContext(
      Project project,
      AndroidFacet facet,
      ExecutionEnvironment env,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    return new BlazeAndroidTestRunContext(
        project, facet, configuration, env, configState, getLabel(), blazeFlags, exeFlags);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    BlazeAndroidRunConfigurationValidationUtil.throwTopConfigurationError(validate());
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning. We use a separate method for the collection so the compiler prevents us from
   * accidentally throwing.
   */
  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    Module module =
        ModuleFinder.getInstance(configuration.getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateModule(module));
    errors.addAll(configState.validate(configuration.getProject()));
    return errors;
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    Label target = getLabel();
    if (target == null) {
      return null;
    }
    BlazeConfigurationNameBuilder nameBuilder =
        new BlazeConfigurationNameBuilder(this.configuration);

    boolean isClassTest =
        configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_CLASS;
    boolean isMethodTest =
        configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_METHOD;
    if ((isClassTest || isMethodTest) && configState.getClassName() != null) {
      // Get the class name without the package.
      String className = JavaExecutionUtil.getPresentableClassName(configState.getClassName());
      if (className != null) {
        String targetString = className;
        if (isMethodTest) {
          targetString += "#" + configState.getMethodName();
        }

        if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.NON_BLAZE)) {
          return targetString;
        } else {
          return nameBuilder.setTargetString(targetString).build();
        }
      }
    }
    return nameBuilder.build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.BLAZE_TEST)) {
      return BlazeCommandName.TEST;
    } else if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.MOBILE_INSTALL)) {
      return BlazeCommandName.MOBILE_INSTALL;
    }
    return null;
  }

  @Override
  public String getHandlerName() {
    return "Android Test Handler";
  }
}
