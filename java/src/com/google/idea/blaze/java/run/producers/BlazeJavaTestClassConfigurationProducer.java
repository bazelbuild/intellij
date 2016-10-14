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
package com.google.idea.blaze.java.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Producer for run configurations related to Java test classes in Blaze. */
public class BlazeJavaTestClassConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeJavaTestClassConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      @NotNull BlazeCommandRunConfiguration configuration,
      @NotNull ConfigurationContext context,
      @NotNull Ref<PsiElement> sourceElement) {

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }

    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null) {
      return false;
    }
    sourceElement.set(testClass);

    TestIdeInfo.TestSize testSize = TestSizeAnnotationMap.getTestSize(testClass);
    RuleIdeInfo rule = RunUtil.ruleForTestClass(context.getProject(), testClass, testSize);
    if (rule == null) {
      return false;
    }

    configuration.setTarget(rule.label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.setCommand(BlazeCommandName.TEST);

    ImmutableList.Builder<String> flags = ImmutableList.builder();

    String qualifiedName = testClass.getQualifiedName();
    if (qualifiedName != null) {
      flags.add(BlazeFlags.testFilterFlagForClass(qualifiedName));
    }

    flags.add(BlazeFlags.TEST_OUTPUT_STREAMED);
    flags.addAll(handlerState.getBlazeFlags());

    handlerState.setBlazeFlags(flags.build());

    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(testClass.getName());
    configuration.setName(nameBuilder.build());

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      @NotNull BlazeCommandRunConfiguration configuration, @NotNull ConfigurationContext context) {

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }

    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(contextLocation);
    if (methodLocation != null) {
      return false;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null) {
      return false;
    }

    return checkIfAttributesAreTheSame(configuration, testClass);
  }

  private boolean checkIfAttributesAreTheSame(
      @NotNull BlazeCommandRunConfiguration configuration, @NotNull PsiClass testClass) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    List<String> flags = handlerState.getBlazeFlags();

    return flags.contains(BlazeFlags.testFilterFlagForClass(testClass.getQualifiedName()));
  }
}
