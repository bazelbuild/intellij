/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.scala.run;

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil;
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/** Common functions for handling specs2 test scopes/cases. */
public final class Specs2Utils {
  private Specs2Utils() {}

  @Nullable
  public static ScInfixExpr getContainingTestScope(PsiElement element) {
    return getContainingExpr(element, TestNodeProvider::isSpecs2ScopeExpr);
  }

  @Nullable
  public static ScInfixExpr getContainingTestExpr(PsiElement element) {
    return getContainingExpr(element, TestNodeProvider::isSpecs2TestExpr);
  }

  @Nullable
  public static String getSpecs2ScopeName(ScInfixExpr testScope) {
    String scopeName = getElementStaticText(testScope.lOp());
    if (scopeName == null) {
      return null;
    }
    return scopeName + " " + testScope.operation().refName();
  }

  @Nullable
  public static String getSpecs2TestName(ScInfixExpr testCase) {
    return getElementStaticText(testCase.lOp());
  }

  @Nullable
  public static String getSpecs2ScopedTestName(ScInfixExpr testCase) {
    String testName = getSpecs2TestName(testCase);
    if (testName == null) {
      return null;
    }
    ScInfixExpr testScope = getContainingTestScope(testCase);
    if (testScope == null) {
      return testName;
    }
    String scopeName = getSpecs2ScopeName(testScope);
    if (scopeName == null) {
      return testName;
    }
    return scopeName + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER + testName;
  }

  public static String getSpecs2TestDisplayName(ScTypeDefinition testClass, PsiElement element) {
    String testName = null;
    if (TestNodeProvider.isSpecs2TestExpr(element)) {
      testName = getSpecs2ScopedTestName((ScInfixExpr) element);
    } else if (TestNodeProvider.isSpecs2ScopeExpr(element)) {
      testName = getSpecs2ScopeName((ScInfixExpr) element);
    }
    if (testName == null) {
      return testClass.getName();
    }
    testName = testName.replace(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER, " ");
    return testClass.getName() + "." + testName;
  }

  public static String getTestFilter(ScTypeDefinition testClass, PsiElement testCase) {
    String testName = "";
    if (TestNodeProvider.isSpecs2TestExpr(testCase)) {
      testName = Specs2Utils.getSpecs2ScopedTestName((ScInfixExpr) testCase) + '$';
    } else if (TestNodeProvider.isSpecs2ScopeExpr(testCase)) {
      testName =
          Specs2Utils.getSpecs2ScopeName((ScInfixExpr) testCase)
              + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER;
    }
    return testClass.qualifiedName() + '#' + testName;
  }

  @Nullable
  private static ScInfixExpr getContainingExpr(PsiElement element, Predicate<PsiElement> predicate) {
    while (element != null && !predicate.test(element)) {
      element = PsiTreeUtil.getParentOfType(element, ScInfixExpr.class);
    }
    return (ScInfixExpr) element;
  }

  @Nullable
  private static String getElementStaticText(PsiElement element) {
    return TestConfigurationUtil.getStaticTestNameOrDefault(
            element,
            null,
            false);
  }
}
