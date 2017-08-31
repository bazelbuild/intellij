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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestRunLineMarkerProvider;
import java.util.Arrays;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider;

/**
 * Generates run/debug gutter icons for scala_test, and scala_junit_tests.
 *
 * <p>{@link ScalaTestRunLineMarkerProvider} exists in the scala plugin, but it does not currently
 * try to handle scalatest and specs2 at all. For JUnit tests, it has a bug that causes it to not
 * generate icons for a test without run state (i.e., newly written tests).
 */
public class BlazeScalaTestRunLineMarkerContributor extends ScalaTestRunLineMarkerProvider {
  @Nullable
  @Override
  public Info getInfo(PsiElement e) {
    if (isIdentifier(e)) {
      PsiElement element = e.getParent();
      if (element instanceof ScClass) {
        return getInfo((ScClass) element, null, super.getInfo(e));
      } else if (element instanceof ScFunctionDefinition) {
        ScClass testClass = PsiTreeUtil.getParentOfType(element, ScClass.class);
        if (testClass != null) {
          return getInfo(testClass, element, super.getInfo(e));
        }
      } else if (element instanceof ScReferenceExpression) {
        // TODO: handle infix expressions. E.g., "foo" should "bar" in { baz }
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static Info getInfo(ScClass testClass, @Nullable PsiElement testCase, Info toReplace) {
    TestFramework framework = TestFrameworks.detectFramework(testClass);
    if (framework == null) {
      return null;
    }
    boolean isClass = testCase == null;
    String url;
    if (isClass) {
      if (!framework.isTestClass(testClass)) {
        return null;
      }
      url = "java:suite://" + testClass.getQualifiedName();
    } else if (testCase instanceof ScFunctionDefinition) {
      ScFunctionDefinition method = (ScFunctionDefinition) testCase;
      if (!framework.isTestMethod(method)) {
        return null;
      }
      url = "java:test://" + testClass.getQualifiedName() + "." + method.getName();
    } else if (testCase instanceof ScInfixExpr) {
      // TODO: handle this case.
      return null;
    } else {
      return null;
    }

    return getInfo(url, testClass.getProject(), isClass, toReplace);
  }

  private static Info getInfo(String url, Project project, boolean isClass, Info toReplace) {
    Icon icon = getTestStateIcon(url, project, isClass);
    return new ReplacementInfo(
        icon,
        ExecutorAction.getActions(1),
        RunLineMarkerContributor.RUN_TEST_TOOLTIP_PROVIDER,
        toReplace);
  }

  /** Copied from {@link TestRunLineMarkerProvider#getTestStateIcon(String, Project, boolean)} */
  private static Icon getTestStateIcon(String url, Project project, boolean isClass) {
    TestStateStorage.Record state = TestStateStorage.getInstance(project).getState(url);
    if (state != null) {
      TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
      if (magnitude != null) {
        switch (magnitude) {
          case ERROR_INDEX:
          case FAILED_INDEX:
            return AllIcons.RunConfigurations.TestState.Red2;
          case PASSED_INDEX:
          case COMPLETE_INDEX:
            return AllIcons.RunConfigurations.TestState.Green2;
          default:
        }
      }
    }
    return isClass
        ? AllIcons.RunConfigurations.TestState.Run_run
        : AllIcons.RunConfigurations.TestState.Run;
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
