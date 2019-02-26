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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.java.run.producers.TestSizeFinder;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework;

/**
 * {@link TestContextProvider} for run configurations related to Scala test classes (not handled by
 * JUnit) in Blaze. Handles only {@link ScalaTestTestFramework}.
 */
class ScalaTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    ScClass testClass = getTestClass(context);
    if (testClass == null) {
      return null;
    }
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(
            testClass, TestSizeFinder.getTestSize(testClass));
    if (target == null) {
      return null;
    }
    return TestContext.builder(testClass, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(getTestFilter(testClass))
        .setDescription(testClass.getName())
        .build();
  }

  @Nullable
  private static ScClass getTestClass(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return null;
    }
    return getTestClass(location);
  }

  @Nullable
  private static ScClass getTestClass(Location<?> location) {
    PsiElement element = location.getPsiElement();
    ScClass testClass;
    if (element instanceof ScClass) {
      testClass = (ScClass) element;
    } else {
      testClass = PsiTreeUtil.getParentOfType(element, ScClass.class);
    }
    if (testClass != null && isTestClass(testClass)) {
      return testClass;
    }
    return null;
  }

  private static boolean isTestClass(ScClass testClass) {
    TestFramework framework = TestFrameworks.detectFramework(testClass);
    return framework instanceof ScalaTestTestFramework && framework.isTestClass(testClass);
  }

  private static String getTestFilter(PsiClass testClass) {
    // TODO: may need to append '#' if implementation changes.
    // https://github.com/bazelbuild/rules_scala/pull/216
    return testClass.getQualifiedName();
  }
}
