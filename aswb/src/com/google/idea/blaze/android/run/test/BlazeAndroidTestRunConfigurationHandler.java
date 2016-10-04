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
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandlerEditor;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerEditor;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import javax.swing.Icon;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_test targets.
 */
public class BlazeAndroidTestRunConfigurationHandler
    implements BlazeAndroidRunConfigurationHandler {
  private static final Logger LOG =
      Logger.getInstance(BlazeAndroidTestRunConfigurationHandler.class);

  private final BlazeCommandRunConfiguration configuration;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final BlazeAndroidTestRunConfigurationState configState;
  private final BlazeAndroidRunConfigurationRunner runner;

  BlazeAndroidTestRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
    commonState = new BlazeAndroidRunConfigurationCommonState(ImmutableList.of());
    configState = new BlazeAndroidTestRunConfigurationState();
    runner =
        new BlazeAndroidRunConfigurationRunner(
            configuration.getProject(), this, commonState, true, configuration.getUniqueID());
  }

  @Override
  public BlazeAndroidRunContext createRunContext(
      Project project,
      AndroidFacet facet,
      ExecutionEnvironment env,
      ImmutableList<String> buildFlags) {
    return new BlazeAndroidTestRunContext(
        project, facet, configuration, env, configState, getLabel(), buildFlags);
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

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  @Override
  public BlazeAndroidTestRunConfigurationState getConfigState() {
    return configState;
  }

  @Nullable
  private Module getModule() {
    Label target = getLabel();
    return target != null
        ? BlazeAndroidProjectStructureSyncer.ensureRunConfigurationModule(
            configuration.getProject(), target)
        : null;
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
    validateLabel(errors);
    return errors;
  }

  private void validateLabel(List<ValidationError> errors) {
    Project project = configuration.getProject();
    Label target = getLabel();
    Kind kind = Kind.ANDROID_TEST;
    RuleIdeInfo rule =
        target != null ? RuleFinder.getInstance().ruleForTarget(project, target) : null;
    if (rule == null) {
      errors.add(
          ValidationError.fatal(
              String.format("No existing %s rule selected.", Blaze.buildSystemName(project))));
    } else if (!rule.kindIsOneOf(kind)) {
      errors.add(
          ValidationError.fatal(
              String.format(
                  "Selected %s rule is not %s", Blaze.buildSystemName(project), kind.toString())));
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    commonState.readExternal(element);
    runner.readExternal(element);
    configState.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    commonState.writeExternal(element);
    runner.writeExternal(element);
    configState.writeExternal(element);
  }

  @Override
  public BlazeAndroidTestRunConfigurationHandler cloneFor(
      BlazeCommandRunConfiguration configuration) {
    final Element element = new Element("dummy");
    try {
      writeExternal(element);
      final BlazeAndroidTestRunConfigurationHandler handler =
          new BlazeAndroidTestRunConfigurationHandler(configuration);
      handler.readExternal(element);
      return handler;
    } catch (InvalidDataException | WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  @Nullable
  public final RunProfileState getState(
      @NotNull final Executor executor, @NotNull ExecutionEnvironment env)
      throws ExecutionException {
    final Module module = getModule();
    return runner.getState(module, executor, env);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    return runner.executeBuild(environment);
  }

  @Override
  @Nullable
  public String suggestedName() {
    Label target = getLabel();
    if (target == null) {
      return null;
    }
    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);

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
        return nameBuilder.setTargetString(targetString).build();
      }
    }
    return nameBuilder.build();
  }

  @Override
  public boolean isGeneratedName(boolean hasGeneratedFlag) {
    final String name = configuration.getName();

    if ((configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_CLASS
            || configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_METHOD)
        && (configState.getClassName() == null || configState.getClassName().length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    if (configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_METHOD
        && (configState.getMethodName() == null || configState.getMethodName().length() == 0)) {
      return JavaExecutionUtil.isNewName(name);
    }
    return Comparing.equal(name, suggestedName());
  }

  @Override
  @Nullable
  public String getCommandName() {
    return "test";
  }

  @Override
  public String getHandlerName() {
    return "Android Test Handler";
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return null;
  }

  @Override
  public BlazeCommandRunConfigurationHandlerEditor getHandlerEditor() {
    Project project = configuration.getProject();
    return new BlazeAndroidRunConfigurationHandlerEditor(
        project, new BlazeAndroidTestRunConfigurationStateEditor(project));
  }
}
