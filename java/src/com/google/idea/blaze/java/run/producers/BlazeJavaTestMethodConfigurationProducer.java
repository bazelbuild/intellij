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
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Producer for run configurations related to Java test methods in Blaze. */
public class BlazeJavaTestMethodConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static class TestMethodContext {
    private final PsiMethod firstMethod;
    private final PsiClass containingClass;
    private final List<String> methodNames;
    private final String testFilterFlag;
    private final Label blazeTarget;

    TestMethodContext(
        PsiMethod firstMethod,
        PsiClass containingClass,
        List<String> methodNames,
        String testFilterFlag,
        Label blazeTarget) {
      this.firstMethod = firstMethod;
      this.containingClass = containingClass;
      this.methodNames = methodNames;
      this.testFilterFlag = testFilterFlag;
      this.blazeTarget = blazeTarget;
    }
  }

  public BlazeJavaTestMethodConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {

    TestMethodContext methodContext = getSelectedMethodContext(context);
    if (methodContext == null) {
      return false;
    }

    // PatternConfigurationProducer also chooses the first method as its source element.
    // As long as we choose an element at the same PSI hierarchy level,
    // PatternConfigurationProducer won't override our configuration.
    sourceElement.set(methodContext.firstMethod);

    configuration.setTarget(methodContext.blazeTarget);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(methodContext.testFilterFlag);
    if (!flags.contains(BlazeFlags.DISABLE_TEST_SHARDING)) {
      flags.add(BlazeFlags.DISABLE_TEST_SHARDING);
    }
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(
        String.format(
            "%s.%s",
            methodContext.containingClass.getName(), String.join(",", methodContext.methodNames)));
    configuration.setName(nameBuilder.build());
    configuration.setNameChangedByUser(true); // don't revert to generated name
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }

    TestMethodContext methodContext = getSelectedMethodContext(context);
    return methodContext != null
        && handlerState.getBlazeFlagsState().getRawFlags().contains(methodContext.testFilterFlag)
        && methodContext.blazeTarget.equals(configuration.getTarget());
  }

  @Nullable
  private static TestMethodContext getSelectedMethodContext(ConfigurationContext context) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return null;
    }
    final List<PsiMethod> selectedMethods = TestMethodSelectionUtil.getSelectedMethods(context);
    if (selectedMethods == null) {
      return null;
    }
    assert selectedMethods.size() > 0;
    final PsiMethod firstMethod = selectedMethods.get(0);

    final PsiClass containingClass = firstMethod.getContainingClass();
    if (containingClass == null) {
      return null;
    }
    for (PsiMethod method : selectedMethods) {
      if (!containingClass.equals(method.getContainingClass())) {
        return null;
      }
    }

    TestSize testSize = TestSizeAnnotationMap.getTestSize(firstMethod);
    TargetInfo target = RunUtil.targetForTestClass(containingClass, testSize);
    if (target == null) {
      return null;
    }

    String testFilter =
        BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(containingClass, selectedMethods);
    if (testFilter == null) {
      return null;
    }
    // Sort so multiple configurations created with different selection orders are the same.
    List<String> methodNames =
        selectedMethods.stream().map(PsiMethod::getName).sorted().collect(Collectors.toList());
    final String testFilterFlag = BlazeFlags.TEST_FILTER + "=" + testFilter;
    return new TestMethodContext(
        firstMethod, containingClass, methodNames, testFilterFlag, target.label);
  }
}
