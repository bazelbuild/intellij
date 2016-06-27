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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfiguration;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An extension of the normal Android Studio run configuration for launching Android tests,
 * adapted specifically for selecting and launching android_test targets.
 */
public final class BlazeAndroidTestRunConfiguration extends LocatableConfigurationBase
  implements BlazeAndroidRunConfiguration, RunConfiguration {

  private static final Logger LOG = Logger.getInstance(BlazeAndroidTestRunConfiguration.class);
  private final Project project;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final BlazeAndroidRunConfigurationRunner runner;

  @NotNull private final BlazeAndroidTestRunConfigurationState configState = new BlazeAndroidTestRunConfigurationState();

  public BlazeAndroidTestRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory, "");
    this.project = project;

    RuleIdeInfo rule = RuleFinder.getInstance().firstRuleOfKinds(project, Kind.ANDROID_TEST);
    this.commonState = new BlazeAndroidRunConfigurationCommonState(rule != null ? rule.label : null, ImmutableList.of());
    this.runner = new BlazeAndroidRunConfigurationRunner(project, this, commonState, true, getUniqueID());
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  @NotNull
  public BlazeAndroidTestRunConfigurationState getConfigState() {
    return configState;
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
  public BlazeAndroidRunConfigurationRunner getRunner() {
    return runner;
  }

  @Override
  public BlazeAndroidRunContext createRunContext(Project project,
                                                 AndroidFacet facet,
                                                 ExecutionEnvironment env,
                                                 ImmutableList<String> buildFlags) {
    return new BlazeAndroidTestRunContext(project, facet, this, env, commonState, configState, buildFlags);
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new BlazeAndroidTestRunConfigurationEditor(
      getProject(),
      new BlazeAndroidTestRunConfigurationStateEditor(project)
    );
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    List<ValidationError> errors = validate();
    if (errors.isEmpty()) {
      return;
    }
    ValidationError topError = Ordering.natural().max(errors);
    if (topError.isFatal()) {
      throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
    }
    throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
  }

  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    errors.addAll(runner.validate(getModule()));
    commonState.checkConfiguration(getProject(), Kind.ANDROID_TEST, errors);
    return errors;
  }

  @Override
  @Nullable
  public final RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getModule();
    return runner.getState(module, executor, env);
  }

  private Module getModule() {
    return BlazeAndroidProjectStructureSyncer.ensureRunConfigurationModule(project, getTarget());
  }

  @Override
  @Nullable
  public String suggestedName() {
    Label target = commonState.getTarget();
    if (target == null) {
      return null;
    }
    String name = target.ruleName().toString();
    if (configState.TESTING_TYPE == BlazeAndroidTestRunConfigurationState.TEST_CLASS) {
      name += ": " + configState.CLASS_NAME;
    }
    else if (configState.TESTING_TYPE == BlazeAndroidTestRunConfigurationState.TEST_METHOD) {
      name += ": " + configState.CLASS_NAME + "#" + configState.METHOD_NAME;
    }
    return String.format("%s test: %s", Blaze.buildSystemName(project), name);
  }

  @Override
  public boolean isGeneratedName() {
    final String name = getName();

    if ((configState.TESTING_TYPE == BlazeAndroidTestRunConfigurationState.TEST_CLASS || configState.TESTING_TYPE == BlazeAndroidTestRunConfigurationState.TEST_METHOD) &&
        (configState.CLASS_NAME == null || configState.CLASS_NAME.length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    if (configState.TESTING_TYPE == BlazeAndroidTestRunConfigurationState.TEST_METHOD &&
        (configState.METHOD_NAME == null || configState.METHOD_NAME.length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    return Comparing.equal(name, suggestedName());
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
      BlazeAndroidTestRunConfiguration clone = new BlazeAndroidTestRunConfiguration(
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
