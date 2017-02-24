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

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
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
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
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

    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return false;
    }
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null) {
      return false;
    }
    if (testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    sourceElement.set(testClass);

    TestIdeInfo.TestSize testSize = TestSizeAnnotationMap.getTestSize(testClass);
    TargetIdeInfo target = RunUtil.targetForTestClass(testClass, testSize);
    if (target == null) {
      return false;
    }

    configuration.setTarget(target.key.label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    String testFilter = BlazeJUnitTestFilterFlags.testFilterForClass(testClass);
    if (testFilter == null) {
      return false;
    }
    handlerState.setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(BlazeFlags.TEST_FILTER + "=" + testFilter);
    handlerState.setBlazeFlags(flags);

    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(testClass.getName());
    configuration.setName(nameBuilder.build());
    configuration.setNameChangedByUser(true); // don't revert to generated name
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

    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
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
    String filter = BlazeJUnitTestFilterFlags.testFilterForClass(testClass);
    if (filter == null) {
      return false;
    }
    return Objects.equals(BlazeFlags.TEST_FILTER + "=" + filter, handlerState.getTestFilterFlag());
  }
}
