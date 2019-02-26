/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.base.Strings;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.run.producers.JUnitConfigurationUtil;
import com.google.idea.blaze.java.run.producers.ProducerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Objects;
import javax.annotation.Nullable;

/** Producer for run configurations related to 'android_test' targets in Blaze. */
class AndroidTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return null;
    }
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    PsiMethod method = findTestMethod(location);
    PsiClass testClass =
        method != null ? method.getContainingClass() : JUnitUtil.getTestClass(location);
    return testClass != null ? AndroidTestContext.fromClassAndMethod(testClass, method) : null;
  }

  @Nullable
  private static PsiMethod findTestMethod(Location<?> location) {
    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(location);
    return methodLocation != null ? methodLocation.getPsiElement() : null;
  }

  private static class AndroidTestContext implements RunConfigurationContext {
    @Nullable
    static AndroidTestContext fromClassAndMethod(PsiClass clazz, @Nullable PsiMethod method) {
      TargetInfo target = TestTargetHeuristic.testTargetForPsiElement(clazz, null);
      if (target == null
          || !AndroidBlazeRules.RuleTypes.ANDROID_TEST.getKind().equals(target.getKind())) {
        return null;
      }
      return new AndroidTestContext(clazz, method, target);
    }

    private final PsiClass psiClass;
    @Nullable private final PsiMethod method;
    private final TargetInfo target;

    AndroidTestContext(PsiClass psiClass, @Nullable PsiMethod method, TargetInfo target) {
      this.psiClass = psiClass;
      this.method = method;
      this.target = target;
    }

    @Override
    public PsiElement getSourceElement() {
      return method != null ? method : psiClass;
    }

    @Override
    public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(target);
      BlazeAndroidTestRunConfigurationState configState =
          config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
      if (configState == null) {
        return false;
      }
      configState.setClassName(getClassName());
      configState.setTestingType(getTestingType());
      if (method != null) {
        configState.setMethodName(getMethodName());
      }
      config.setGeneratedName();
      return true;
    }

    private int getTestingType() {
      return method != null
          ? AndroidTestRunConfiguration.TEST_METHOD
          : AndroidTestRunConfiguration.TEST_CLASS;
    }

    @Override
    public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
      BlazeAndroidTestRunConfigurationState configState =
          config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
      if (configState == null) {
        return false;
      }
      return configState.getTestingType() == getTestingType()
          && Objects.equals(Strings.emptyToNull(configState.getClassName()), getClassName())
          && Objects.equals(Strings.emptyToNull(configState.getMethodName()), getMethodName());
    }

    @Nullable
    private String getMethodName() {
      return method != null ? ReadAction.compute(() -> method.getName()) : null;
    }

    @Nullable
    private String getClassName() {
      return ReadAction.compute(() -> psiClass.getQualifiedName());
    }
  }
}
