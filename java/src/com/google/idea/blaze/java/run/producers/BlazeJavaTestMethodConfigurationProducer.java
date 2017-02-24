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

  private static class SelectedMethodInfo {
    private final PsiMethod firstMethod;
    private final PsiClass containingClass;
    private final List<String> methodNames;
    private final String testFilterFlag;

    public SelectedMethodInfo(
        PsiMethod firstMethod,
        PsiClass containingClass,
        List<String> methodNames,
        String testFilterFlag) {
      this.firstMethod = firstMethod;
      this.containingClass = containingClass;
      this.methodNames = methodNames;
      this.testFilterFlag = testFilterFlag;
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

    SelectedMethodInfo methodInfo = getSelectedMethodInfo(context);
    if (methodInfo == null) {
      return false;
    }

    // PatternConfigurationProducer also chooses the first method as its source element.
    // As long as we choose an element at the same PSI hierarchy level,
    // PatternConfigurationProducer won't override our configuration.
    sourceElement.set(methodInfo.firstMethod);

    TestIdeInfo.TestSize testSize = TestSizeAnnotationMap.getTestSize(methodInfo.firstMethod);
    TargetIdeInfo target = RunUtil.targetForTestClass(methodInfo.containingClass, testSize);
    if (target == null) {
      return false;
    }

    configuration.setTarget(target.key.label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(methodInfo.testFilterFlag);
    handlerState.setBlazeFlags(flags);

    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(
        String.format(
            "%s.%s",
            methodInfo.containingClass.getName(), String.join(",", methodInfo.methodNames)));
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
    if (!Objects.equals(handlerState.getCommand(), BlazeCommandName.TEST)) {
      return false;
    }

    SelectedMethodInfo methodInfo = getSelectedMethodInfo(context);
    if (methodInfo == null) {
      return false;
    }

    List<String> flags = handlerState.getBlazeFlags();
    return flags.contains(methodInfo.testFilterFlag);
  }

  @Nullable
  private static SelectedMethodInfo getSelectedMethodInfo(ConfigurationContext context) {
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
    String testFilter =
        BlazeJUnitTestFilterFlags.testFilterForClassAndMethods(containingClass, selectedMethods);
    if (testFilter == null) {
      return null;
    }
    // Sort so multiple configurations created with different selection orders are the same.
    List<String> methodNames =
        selectedMethods.stream().map(PsiMethod::getName).sorted().collect(Collectors.toList());
    final String testFilterFlag = BlazeFlags.TEST_FILTER + "=" + testFilter;
    return new SelectedMethodInfo(firstMethod, containingClass, methodNames, testFilterFlag);
  }
}
