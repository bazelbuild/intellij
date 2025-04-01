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
package com.google.idea.blaze.golang.run.producers;

import javax.annotation.Nullable;

import com.goide.execution.testing.GoTestFinder;
import com.goide.execution.testing.GoTestRunConfigurationProducerBase;
import com.goide.execution.testing.frameworks.testify.GoTestifySupport;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionOrMethodDeclaration;
import com.goide.psi.GoMethodDeclaration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

class GoTestContextProvider implements TestContextProvider {

  /**
   * Given a code element, calculate the test filter we'd need to run exactly that element.
   * It takes into account nested tests.
   * Here is an example of the expected results of clicking on each section:
   * ```
   * func Test(t *testing.T) {                    // returns "Test"
   *   t.Run("with_nested", func(t *testing.T) {  // returns "Test/with_nested"
   * 	    t.Run("subtest", func(t *testing.T) {}) // returns "Test/with_nested/subtest"
   *   })
   * }
   * ```
   * When a user clicks in the ">" button, we get pointed to the "Run" part of "t.Run".
   * We need to walk the tree up to see the function call that has the actual argument.
   *
   * This function doesn't worry about turning this filter into a regex which can be passed to --test_filter.
   * @see #regexifyTestFilter for converting this to a --test_filter value.
   *
   * @param element: Element the user has clicked on.
   * @param enclosingFunction: Go function encompassing this test.
   * @return String representation of the proper test filter.
   */
  private static String calculateRawTestFilterForElement(PsiElement element, GoFunctionOrMethodDeclaration enclosingFunction) {
    if (element.getParent().isEquivalentTo(enclosingFunction)) {
      return enclosingFunction.getName();
    } else {
      return GoTestRunConfigurationProducerBase.findSubTestInContext(element, enclosingFunction);
    }
  }

  @VisibleForTesting
  static String regexifyTestFilter(String testFilter) {
    return "^" + escapeRegexChars(testFilter) + "$";
  }

  private static String escapeRegexChars(String name) {
    StringBuilder output = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (isRegexCharNeedingEscaping(c)) {
        output.append("\\");
      }
      output.append(c);
    }
    return output.toString();
  }

  private static boolean isRegexCharNeedingEscaping(char c) {
    // Taken from https://cs.opensource.google/go/go/+/refs/tags/go1.21.4:src/regexp/regexp.go;l=720
    return c == '\\' || c == '.' || c == '+' || c == '*' || c == '?' || c == '(' || c == ')' || c == '|' || c == '[' || c == ']' || c == '{' || c == '}' || c == '^' || c == '$';
  }


  public static final String GO_TEST_WRAP_TESTV = "GO_TEST_WRAP_TESTV=1";
  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (!(file instanceof GoFile) || !GoTestFinder.isTestFile(file)) {
      return null;
    }
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(element, /* testSize= */ null);
    if (target == null) {
      return null;
    }
    GoFunctionOrMethodDeclaration function = GoTestFinder.findTestFunctionInContext(element);
    if (function == null) {
      return TestContext.builder(/* sourceElement= */ file, ExecutorType.DEBUG_SUPPORTED_TYPES)
          .addTestEnv(GO_TEST_WRAP_TESTV)
          .setTarget(target)
          .setDescription(file.getName())
          .build();
    }

    String testFilterRegex = regexifyTestFilter(calculateRawTestFilterForElement(element, function));
    TestContext.Builder builder = TestContext.builder(/* sourceElement= */ function,
            ExecutorType.DEBUG_SUPPORTED_TYPES)
        .addTestEnv(GO_TEST_WRAP_TESTV)
        .setTarget(target)
        .setDescription(String.format("%s#%s", file.getName(), function.getName()));
    if (function instanceof GoMethodDeclaration method &&
        GoTestifySupport.getTestifySuiteTypeSpec(method) != null) {
      builder.addBlazeFlagsModification(TestContext.BlazeFlagsModification.addFlagIfNotPresent(
          BlazeFlags.TEST_ARG + "-testify.m=" + testFilterRegex));
    } else {
      builder.setTestFilter(testFilterRegex);
    }
    return builder.build();
  }
}
