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
package com.google.idea.blaze.clwb.run.producers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.clwb.run.test.GoogleTestLocation;
import com.google.idea.blaze.clwb.run.test.GoogleTestSpecification;
import com.google.idea.blaze.cpp.CppBlazeRules.RuleTypes;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.psi.OCDeclarator;
import com.jetbrains.cidr.lang.types.OCFunctionType;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Provides run configurations related to C/C++ test classes in Blaze.
 */
class CppTestContextProvider implements TestContextProvider {

  private static RunConfigurationContext getBazelTargetContext(PsiElement element, PsiFile file,
      ConfigurationContext context) {
    // Ensure we're in a main function, as defined by the C++ standard:
    //   - https://en.cppreference.com/w/cpp/language/main_function
    if (!(element.getContext() instanceof OCDeclarator)) {
      return null;
    }
    OCDeclarator psiDeclaration = (OCDeclarator) element.getContext();
    if (!(psiDeclaration.getResolvedType() instanceof OCFunctionType)) {
      return null;
    }

    String funcName = psiDeclaration.getName();
    String retTypeName = ((OCFunctionType) psiDeclaration.getResolvedType()).getReturnType()
        .getName();
    if (!(funcName.equals("main") && retTypeName.equals("int"))) {
      return null;
    }

    // We pass null as the test size because the TestTargetHeuristicts that we expect to accept this
    // don't care about the test size.
    ListenableFuture<TargetInfo> targetFuture = TestTargetHeuristic.targetFutureForPsiElement(
        element, null);
    if (targetFuture == null
        || targetFuture.isCancelled()
        || (targetFuture.isDone() && FuturesUtil.getIgnoringErrors(targetFuture) == null)) {
      return null;
    }
    Executor executor =
        ApplicationManager.getApplication().isUnitTestMode()
            ? MoreExecutors.directExecutor()
            : PooledThreadExecutor.INSTANCE;
    ListenableFuture<TargetInfo> ccTestFuture = Futures.transform(targetFuture,
        targetInfo -> {
          if (targetInfo.getKind().equals(RuleTypes.CC_TEST.getKind())) {
            return targetInfo;
          } else {
            return null;
          }
        }, executor);
    return TestContext.builder(element, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(ccTestFuture)
        .build();
  }

  private static RunConfigurationContext getGTestContext(PsiElement element, PsiFile file,
      ConfigurationContext context) {
    GoogleTestLocation test = GoogleTestLocation.findGoogleTest(element, context.getProject());
    if (test == null) {
      return null;
    }
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(test.getPsiElement(), /* testSize= */ null);
    if (target == null) {
      return null;
    }
    GoogleTestSpecification gtest = test.gtest;
    String description =
        gtest.description() != null
            ? String.format("%s (%s)", gtest.description(), file.getName())
            : file.getName();

    return TestContext.builder(test.getPsiElement(), ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(gtest.testFilter())
        .setDescription(description)
        .build();
  }

  /**
   * The single selected {@link PsiElement}. Returns null if multiple elements are selected.
   */
  @Nullable
  private static PsiElement selectedPsiElement(ConfigurationContext context) {
    PsiElement[] psi = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext());
    if (psi != null && psi.length > 1) {
      return null; // multiple elements selected.
    }
    Location<?> location = context.getLocation();
    return location != null ? location.getPsiElement() : null;
  }

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement element = selectedPsiElement(context);
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }

    // Test each test context provider and return the one that matches.

    // Is it a Gtest?
    RunConfigurationContext gTestContext = getGTestContext(element, file, context);
    if (gTestContext != null) {
      return gTestContext;
    }

    // Is it just a main function inside a `cc_test`?
    RunConfigurationContext bazelTargetContext = getBazelTargetContext(element, file, context);
    return bazelTargetContext;
  }
}
