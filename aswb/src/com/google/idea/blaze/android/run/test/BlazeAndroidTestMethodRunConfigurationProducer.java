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

import com.google.common.base.Strings;
import com.google.idea.blaze.android.compatibility.Compatibility.AndroidTestRunConfiguration;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.JUnitConfigurationUtil;
import com.google.idea.blaze.java.run.producers.ProducerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 * Producer for run configurations related to Android test methods in Blaze.
 *
 * <p>This class is based on {@link
 * org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer}.
 */
public class BlazeAndroidTestMethodRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeAndroidTestMethodRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return false;
    }
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(contextLocation);
    if (methodLocation == null) {
      return false;
    }

    final PsiMethod psiMethod = methodLocation.getPsiElement();
    sourceElement.set(psiMethod);

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }

    TargetIdeInfo target = RunUtil.targetForTestClass(containingClass, null);
    if (target == null) {
      return false;
    }
    if (!target.kindIsOneOf(Kind.ANDROID_TEST)) {
      return false;
    }
    configuration.setTarget(target.key.label);
    BlazeAndroidTestRunConfigurationState configState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }
    configState.setTestingType(AndroidTestRunConfiguration.TEST_METHOD);
    configState.setClassName(containingClass.getQualifiedName());
    configState.setMethodName(psiMethod.getName());
    configuration.setGeneratedName();

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return false;
    }
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(contextLocation);
    if (methodLocation == null) {
      return false;
    }

    final PsiMethod psiMethod = methodLocation.getPsiElement();
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }

    return checkIfAttributesAreTheSame(configuration, psiMethod);
  }

  private static boolean checkIfAttributesAreTheSame(
      BlazeCommandRunConfiguration configuration, PsiMethod testMethod) {
    BlazeAndroidTestRunConfigurationState configState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }
    if (Strings.isNullOrEmpty(configState.getClassName())
        || Strings.isNullOrEmpty(configState.getMethodName())) {
      return false;
    }

    return configState.getTestingType() == AndroidTestRunConfiguration.TEST_METHOD
        && configState.getClassName().equals(testMethod.getContainingClass().getQualifiedName())
        && configState.getMethodName().equals(testMethod.getName());
  }
}
