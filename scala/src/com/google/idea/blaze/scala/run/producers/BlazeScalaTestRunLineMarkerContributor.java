/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.scala.run.Specs2Utils;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.io.URLUtil;
import java.util.Arrays;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider;
import org.jetbrains.plugins.scala.testingSupport.test.ui.ScalaTestRunLineMarkerProvider;

/**
 * Generates run/debug gutter icons for scala_test, and scala_junit_tests.
 *
 * <p>{@code ScalaTestRunLineMarkerProvider} exists in the scala plugin, but it does not currently
 * try to handle scalatest and specs2 at all. For JUnit tests, it has a bug that causes it to not
 * generate icons for a test without run state (i.e., newly written tests).
 * https://github.com/JetBrains/intellij-scala/pull/381
 */
public class BlazeScalaTestRunLineMarkerContributor extends ScalaTestRunLineMarkerProvider {
  @Nullable
  @Override
  public Info getInfo(PsiElement element) {
    if (isIdentifier(element)) {
      PsiElement testElement = element.getParent();
      if (testElement instanceof ScClass) {
        return getInfo((ScClass) testElement, null, super.getInfo(element));
      }
      ScClass testClass = PsiTreeUtil.getParentOfType(testElement, ScClass.class);
      if (testClass == null) {
        return null;
      }
      if (testElement instanceof ScFunctionDefinition) {
        return getInfo(testClass, testElement, super.getInfo(element));
      }
      if (testElement.getParent() instanceof ScInfixExpr) {
        ScInfixExpr infixExpr = (ScInfixExpr) testElement.getParent();
        if (infixExpr.operation().equals(testElement)) {
          return getInfo(testClass, infixExpr, super.getInfo(element));
        }
      }
    }
    return null;
  }

  @Override
  public boolean isIdentifier(PsiElement element) {
    return element instanceof LeafPsiElement
        && ((LeafPsiElement) element).getElementType().equals(ScalaTokenTypes.tIDENTIFIER);
  }

  @Nullable
  private static Info getInfo(ScClass testClass, @Nullable PsiElement testCase, Info toReplace) {
    TestFramework framework = TestFrameworks.detectFramework(testClass);
    if (framework == null) {
      return null;
    }
    String url = getTestUrl(framework, testClass, testCase);
    if (url == null) {
      return null;
    }
    return getInfo(url, testClass.getProject(), testCase == null, toReplace);
  }

  @Nullable
  private static String getTestUrl(
      TestFramework framework, ScClass testClass, @Nullable PsiElement testCase) {
    if (testCase instanceof ScFunctionDefinition) {
      return getTestMethodUrl(framework, testClass, (ScFunctionDefinition) testCase);
    } else if (testCase instanceof ScInfixExpr) {
      return getSpecs2TestUrl(testClass, (ScInfixExpr) testCase);
    }
    return getTestClassUrl(framework, testClass);
  }

  @Nullable
  private static String getTestClassUrl(TestFramework framework, ScClass testClass) {
    if (!framework.isTestClass(testClass)) {
      return null;
    }
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + testClass.getQualifiedName();
  }

  @Nullable
  private static String getTestMethodUrl(
      TestFramework framework, ScClass testClass, ScFunctionDefinition method) {
    if (!framework.isTestMethod(method)) {
      return null;
    }
    return SmRunnerUtils.GENERIC_TEST_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + testClass.getQualifiedName()
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + method.getName();
  }

  @Nullable
  private static String getSpecs2TestUrl(ScClass testClass, ScInfixExpr testCase) {
    String name = null;
    String protocol = null;
    if (TestNodeProvider.isSpecs2ScopeExpr(testCase)) {
      protocol = SmRunnerUtils.GENERIC_SUITE_PROTOCOL;
      name = Specs2Utils.getSpecs2ScopeName(testCase);
    } else if (TestNodeProvider.isSpecs2Expr(testCase)) {
      protocol = SmRunnerUtils.GENERIC_TEST_PROTOCOL;
      name = Specs2Utils.getSpecs2ScopedTestName(testCase);
    }
    if (name == null) {
      return null;
    }
    return protocol
        + URLUtil.SCHEME_SEPARATOR
        + testClass.getQualifiedName()
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name;
  }

  private static Info getInfo(String url, Project project, boolean isClass, Info toReplace) {
    Icon icon = getTestStateIcon(url, project, isClass);
    return new ReplacementInfo(
        icon,
        ExecutorAction.getActions(1),
        RunLineMarkerContributor.RUN_TEST_TOOLTIP_PROVIDER,
        toReplace);
  }

  private static class ReplacementInfo extends Info {
    private final Info toReplace;

    ReplacementInfo(
        Icon icon,
        AnAction[] actions,
        Function<PsiElement, String> tooltipProvider,
        Info toReplace) {
      super(icon, actions, tooltipProvider);
      this.toReplace = toReplace;
    }

    @Override
    public boolean shouldReplace(Info other) {
      return toReplace != null && Arrays.equals(toReplace.actions, other.actions);
    }
  }
}
