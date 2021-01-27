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
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.blaze.java.run.producers.JUnitConfigurationUtil;
import com.google.idea.blaze.java.run.producers.ProducerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.annotation.Nullable;
import java.util.Objects;

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

    // Handle java android test context
    PsiMethod method = findTestMethod(location);
    PsiClass testClass =
            method != null ? method.getContainingClass() : JUnitUtil.getTestClass(location);
    if (testClass != null) {
      return AndroidTestContext.fromClassAndMethod(testClass, method);
    }

    // Handle kotlin android test context
    PsiElement element = location.getPsiElement();
    KtNamedFunction ktTestMethod = PsiUtils.getParentOfType(element, KtNamedFunction.class, false);
    KtClass ktTestClass = PsiUtils.getParentOfType(element, KtClass.class, false);
    if (ktTestClass == null) {
      return null;
    }
    TargetInfo target =
            TestTargetHeuristic.testTargetForPsiElement(ktTestClass, /* testSize= */ null);
    if (target == null) {
      return null;
    }
    return AndroidKtTestContext.fromClassAndMethod(ktTestClass, ktTestMethod);
  }

  @Nullable
  private static PsiMethod findTestMethod(Location<?> location) {
    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(location);
    return methodLocation != null ? methodLocation.getPsiElement() : null;
  }

  /** Base abstract context for android test, serves both java and kotlin context**/
  private static abstract class AndroidTestContextBase implements RunConfigurationContext {

    /**
     * @return PsiElement for (java/kotlin) method element
     */
    abstract PsiElement getPsiMethod();
    /**
     * @return PsiElement for class element
     */
    abstract PsiElement getPsiClass();
    abstract TargetInfo getTargetInfo();
    abstract String getMethodName();
    /**
     * @return the full qualified name for the class. e.g. com.example.test.SanityTest
     */
    abstract String getClassName();

    @Override
    public PsiElement getSourceElement() {
      return getPsiMethod() != null ? getPsiMethod() : getPsiClass();
    }

    @Override
    public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(getTargetInfo());
      BlazeAndroidTestRunConfigurationState configState =
              config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
      if (configState == null) {
        return false;
      }
      configState.setClassName(getClassName());
      configState.setTestingType(getTestingType());
      if (getPsiMethod() != null) {
        configState.setMethodName(getMethodName());
      }
      config.setGeneratedName();
      return true;
    }

    private int getTestingType() {
      return getPsiMethod() != null
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
  }

  private static class AndroidTestContext extends AndroidTestContextBase {
    @Nullable
    static AndroidTestContext fromClassAndMethod(PsiClass clazz, @Nullable PsiMethod method) {
      TargetInfo target = TestTargetHeuristic.testTargetForPsiElement(clazz, null);
      if (target == null) {
        return null;
      }

      if (RuleTypes.ANDROID_TEST.getKind().equals(target.getKind())
          || RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind().equals(target.getKind())) {
        return new AndroidTestContext(clazz, method, target);
      }

      return null;
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
    PsiElement getPsiMethod() {
      return method;
    }

    @Override
    PsiElement getPsiClass() {
      return psiClass;
    }

    @Override
    TargetInfo getTargetInfo() {
      return target;
    }

    @Nullable
    String getMethodName() {
      return method != null ? ReadAction.compute(() -> method.getName()) : null;
    }

    @Nullable
    String getClassName() {
      return ReadAction.compute(() -> psiClass.getQualifiedName());
    }
  }

  private static class AndroidKtTestContext extends AndroidTestContextBase {
    @Nullable
    static AndroidKtTestContext fromClassAndMethod(KtClass clazz, @Nullable KtNamedFunction method) {
      TargetInfo target = TestTargetHeuristic.testTargetForPsiElement(clazz, null);
      if (target == null) {
        return null;
      }

      if (RuleTypes.ANDROID_TEST.getKind().equals(target.getKind())
              || RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind().equals(target.getKind())
      ) {
        return new AndroidKtTestContext(clazz, method, target);
      }

      return null;
    }

    private final KtClass psiClass;
    @Nullable private final KtNamedFunction method;
    private final TargetInfo target;

    AndroidKtTestContext(KtClass psiClass, @Nullable KtNamedFunction method, TargetInfo target) {
      this.psiClass = psiClass;
      this.method = method;
      this.target = target;
    }

    @Override
    PsiElement getPsiMethod() {
      return method;
    }

    @Override
    PsiElement getPsiClass() {
      return psiClass;
    }

    @Override
    TargetInfo getTargetInfo() {
      return target;
    }

    @Nullable
    String getMethodName() {
      return method != null ? ReadAction.compute(method::getName) : null;
    }

    @Nullable
    String getClassName() {
      return ReadAction.compute(() -> psiClass.getFqName().asString());
    }
  }
}
