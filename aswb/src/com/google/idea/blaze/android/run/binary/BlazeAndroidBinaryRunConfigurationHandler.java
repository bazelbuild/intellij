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
package com.google.idea.blaze.android.run.binary;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeAndroidBinaryMobileInstallRunContext;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_binary targets.
 */
public class BlazeAndroidBinaryRunConfigurationHandler
    implements BlazeAndroidRunConfigurationHandler {

  private final BlazeCommandRunConfiguration configuration;
  private final BlazeAndroidBinaryRunConfigurationState configState;

  @VisibleForTesting
  protected BlazeAndroidBinaryRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
    configState =
        new BlazeAndroidBinaryRunConfigurationState(
            Blaze.buildSystemName(configuration.getProject()));
  }

  @Override
  public BlazeAndroidBinaryRunConfigurationState getState() {
    return configState;
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return configState.getCommonState();
  }

  @Override
  @Nullable
  public Label getLabel() {
    TargetExpression target = configuration.getTarget();
    if (target instanceof Label) {
      return (Label) target;
    }
    return null;
  }

  @Nullable
  public Module getModule() {
    Label target = getLabel();
    return target != null
        ? BlazeAndroidProjectStructureSyncer.ensureRunConfigurationModule(
            configuration.getProject(), target)
        : null;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) throws ExecutionException {
    Project project = environment.getProject();

    Module module = getModule();
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeAndroidRunConfigurationValidationUtil.validateExecution(module, facet, projectViewSet);

    ImmutableList<String> blazeFlags =
        configState
            .getCommonState()
            .getExpandedBuildFlags(project, projectViewSet, BlazeCommandName.RUN);
    ImmutableList<String> exeFlags =
        ImmutableList.copyOf(configState.getCommonState().getExeFlagsState().getExpandedFlags());
    BlazeAndroidRunContext runContext =
        createRunContext(project, facet, environment, blazeFlags, exeFlags);

    return new BlazeAndroidRunConfigurationRunner(
        module,
        runContext,
        getCommonState().getDeployTargetManager(),
        getCommonState().getDebuggerManager(),
        configuration.getUniqueID());
  }

  private BlazeAndroidRunContext createRunContext(
      Project project,
      AndroidFacet facet,
      ExecutionEnvironment env,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    switch (configState.getLaunchMethod()) {
      case MOBILE_INSTALL:
      case MOBILE_INSTALL_V2:
        return new BlazeAndroidBinaryMobileInstallRunContext(
            project, facet, configuration, env, configState, getLabel(), blazeFlags, exeFlags);
      case NON_BLAZE:
        return new BlazeAndroidBinaryNormalBuildRunContext(
            project, facet, configuration, env, configState, getLabel(), blazeFlags);
    }
    throw new AssertionError();
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
    Module module = getModule();
    errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateModule(module));
    AndroidFacet facet = null;
    if (module != null) {
      facet = AndroidFacet.getInstance(module);
      errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateFacet(facet, module));
    }
    errors.addAll(configState.validate(facet));
    errors.addAll(
        BlazeAndroidRunConfigurationValidationUtil.validateLabel(
            getLabel(), configuration.getProject(), Kind.ANDROID_BINARY));
    return errors;
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    Label target = getLabel();
    if (target == null) {
      return null;
    }
    // buildSystemName and commandName are intentionally omitted.
    return new BlazeConfigurationNameBuilder().setTargetString(target).build();
  }

  @Override
  @Nullable
  public String getCommandName() {
    return null;
  }

  @Override
  public String getHandlerName() {
    return "Android Binary Handler";
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return null;
  }
}
