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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfiguration;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.binary.instantrun.BlazeAndroidBinaryInstantRunContext;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeAndroidBinaryMobileInstallRunContext;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerIconProvider;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * An extension of the normal Android Studio run configuration for launching Android applications,
 * adapted specifically for selecting and launching android_binary targets.
 */
public final class BlazeAndroidBinaryRunConfiguration extends LocatableConfigurationBase
  implements BlazeAndroidRunConfiguration, RunConfiguration, RunnerIconProvider {
  private static final Logger LOG = Logger.getInstance(BlazeAndroidBinaryRunConfiguration.class);
  private final Project project;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final BlazeAndroidBinaryRunConfigurationState configState = new BlazeAndroidBinaryRunConfigurationState();
  private final BlazeAndroidRunConfigurationRunner runner;

  public BlazeAndroidBinaryRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory, "");
    this.project = project;

    RuleIdeInfo rule = RuleFinder.getInstance().firstRuleOfKinds(project, Kind.ANDROID_BINARY);
    this.commonState = new BlazeAndroidRunConfigurationCommonState(rule != null ? rule.label : null, ImmutableList.of());
    this.runner = new BlazeAndroidRunConfigurationRunner(project, this, this.commonState, false, getUniqueID());
  }

  @Nullable
  @Override
  public final Label getTarget() {
    return commonState.getTarget();
  }

  public final void setTarget(@Nullable Label target) {
    commonState.setTarget(target);
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  @Override
  public BlazeAndroidRunConfigurationRunner getRunner() {
    return runner;
  }

  @Override
  public BlazeAndroidRunContext createRunContext(Project project,
                                                 AndroidFacet facet,
                                                 ExecutionEnvironment env,
                                                 ImmutableList<String> buildFlags) {
    if (configState.isInstantRun()) {
      return new BlazeAndroidBinaryInstantRunContext(project, facet, this, env, commonState, configState, buildFlags);
    }
    else if (configState.isMobileInstall()) {
      return new BlazeAndroidBinaryMobileInstallRunContext(project, facet, this, env, commonState, configState, buildFlags);
    }
    else {
      return new BlazeAndroidBinaryNormalBuildRunContext(project, facet, this, env, commonState, configState, buildFlags);
    }
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new BlazeAndroidBinaryRunConfigurationEditor(
      getProject(),
      new BlazeAndroidBinaryRunConfigurationStateEditor(getProject())
    );
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    List<ValidationError> errors = validate();
    if (errors.isEmpty()) {
      return;
    }
    // TODO: Do something with the extra error information? Error count?
    ValidationError topError = Ordering.natural().max(errors);
    if (topError.isFatal()) {
      throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
    }
    throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
  }

  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    errors.addAll(runner.validate(getModule()));
    commonState.checkConfiguration(getProject(), Kind.ANDROID_BINARY, errors);
    return errors;
  }

  private Module getModule() {
    return BlazeAndroidProjectStructureSyncer.ensureRunConfigurationModule(project, getTarget());
  }

  @Override
  @Nullable
  public final RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getModule();
    return runner.getState(module, executor, env);
  }

  @NotNull
  public BlazeAndroidBinaryRunConfigurationState getConfigState() {
    return configState;
  }

  @Nullable
  @Override
  public Icon getExecutorIcon(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    if (!configState.isInstantRun()) {
      return null;
    }

    AndroidSessionInfo info = AndroidSessionInfo.findOldSession(getProject(), null, getUniqueID());
    if (info == null || !info.isInstantRun() || !info.getExecutorId().equals(executor.getId())) {
      return null;
    }

    // Make sure instant run is supported on the relevant device, if found.
    AndroidVersion androidVersion = InstantRunManager.getMinDeviceApiLevel(info.getProcessHandler());
    if (!InstantRunManager.isInstantRunCapableDeviceVersion(androidVersion)) {
      return null;
    }

    return executor instanceof DefaultRunExecutor
           ? AndroidIcons.RunIcons.Replay
           : AndroidIcons.RunIcons.DebugReattach;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    commonState.readExternal(element);
    runner.readExternal(element);;
    configState.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    commonState.writeExternal(element);
    runner.writeExternal(element);;
    configState.writeExternal(element);
  }

  @Override
  public RunConfiguration clone() {
    final Element element = new Element("dummy");
    try {
      writeExternal(element);
      BlazeAndroidBinaryRunConfiguration clone = new BlazeAndroidBinaryRunConfiguration(
        getProject(), getFactory());
      clone.readExternal(element);
      return clone;
    } catch (InvalidDataException e) {
      LOG.error(e);
      return null;
    } catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }
}
