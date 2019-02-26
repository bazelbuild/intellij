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
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.java.run.producers.TestSizeFinder;
import com.google.idea.blaze.scala.run.Specs2Utils;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.util.PsiTreeUtil;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

/**
 * {@link TestContextProvider} for run configurations related to Scala specs2 test expressions in
 * Blaze.
 */
class ScalaSpecs2TestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    ScInfixExpr testCase = Specs2Utils.getContainingTestExprOrScope(context.getPsiLocation());
    if (testCase == null) {
      return null;
    }
    ScTypeDefinition testClass = PsiTreeUtil.getParentOfType(testCase, ScTypeDefinition.class);
    if (testClass == null) {
      return null;
    }
    TestSize testSize = TestSizeFinder.getTestSize(testClass);
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(testClass, testSize);
    if (target == null) {
      return null;
    }
    String testFilter = Specs2Utils.getTestFilter(testClass, testCase);
    String description = Specs2Utils.getSpecs2TestDisplayName(testClass, testCase);
    return TestContext.builder(testCase, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(testFilter)
        .setDescription(description)
        .build();
  }
}
