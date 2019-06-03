/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript.run.producers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.lang.javascript.frameworks.gcl.JSGclModuleReference;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSExpressionStatement;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSVarStatement;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

class JavascriptTestContextProvider implements TestContextProvider {
  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    JSFile file =
        Optional.of(context)
            .map(ConfigurationContext::getPsiLocation)
            .map(PsiElement::getContainingFile)
            .filter(JSFile.class::isInstance)
            .map(JSFile.class::cast)
            .orElse(null);
    if (file == null) {
      return null;
    }
    ListenableFuture<TargetInfo> targetFuture =
        TestTargetHeuristic.targetFutureForPsiElement(file, null);
    if (targetFuture == null) {
      return null;
    }
    targetFuture =
        Futures.transform(
            targetFuture,
            (target) ->
                target != null && ReadAction.compute(() -> isTestFile(file)) ? target : null,
            ApplicationManager.getApplication().isUnitTestMode()
                ? MoreExecutors.directExecutor()
                : PooledThreadExecutor.INSTANCE);
    return TestContext.builder(file, ExecutorType.DEBUG_UNSUPPORTED_TYPES)
        .setTarget(targetFuture)
        .setTestFilter(getTestFilter(file))
        .setDescription(file.getName())
        .build();
  }

  private static boolean isTestFile(JSFile file) {
    return file.isTestFile() || hasTopLevelTests(file) || isClosureTestSuite(file);
  }

  /** jsunit_test can just be top level functions with a test prefix. */
  private static boolean hasTopLevelTests(JSFile file) {
    return Arrays.stream(file.getChildren())
        .filter(JSFunction.class::isInstance)
        .map(JSFunction.class::cast)
        .map(JSFunction::getName)
        .filter(Objects::nonNull)
        .anyMatch(name -> name.startsWith("test"));
  }

  /**
   * A closure test suite will contain
   *
   * <pre>goog.require('goog.testing.testSuite')</pre>
   *
   * and call the imported symbol as a top level statement.
   */
  private static boolean isClosureTestSuite(JSFile file) {
    PsiElement testSuite = null;
    for (PsiElement element : file.getChildren()) {
      if (testSuite == null) {
        testSuite = findTestSuiteReference(element);
      } else {
        if (isTestSuiteCalled(testSuite, element)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement findTestSuiteReference(PsiElement element) {
    if (element instanceof JSVarStatement) {
      // variable assignment might be
      // testSuite = goog.require('goog.testing.testSuite')
      JSVariable variable = PsiTreeUtil.getChildOfType(element, JSVariable.class);
      if (variable != null && isImportingTestSuite(variable)) {
        return variable;
      }
    } else if (element instanceof JSExpressionStatement) {
      // expression statement might be
      // goog.require('goog.testing.testSuite')
      if (isImportingTestSuite(element)) {
        JSLiteralExpression literal =
            PsiTreeUtil.findChildOfType(element, JSLiteralExpression.class);
        // this should be 'goog.testing.testSuite'
        if (literal == null) {
          return null;
        }
        for (PsiReference reference : literal.getReferences()) {
          if (reference instanceof JSGclModuleReference) {
            // this should be testSuite, and should resolve to the function
            return reference.resolve();
          }
        }
      }
    }
    return null;
  }

  private static boolean isTestSuiteCalled(PsiElement testSuite, PsiElement element) {
    if (!(element instanceof JSExpressionStatement)) {
      return false;
    }
    JSCallExpression call = PsiTreeUtil.getChildOfType(element, JSCallExpression.class);
    if (call == null) {
      return false;
    }
    PsiReference reference = call.getMethodExpression().getReference();
    if (reference == null) {
      return false;
    }
    return reference.resolve() == testSuite;
  }

  private static boolean isImportingTestSuite(PsiElement element) {
    JSCallExpression call = PsiTreeUtil.getChildOfType(element, JSCallExpression.class);
    if (call == null || !Objects.equals(call.getMethodExpression().getText(), "goog.require")) {
      return false;
    }
    JSExpression[] arguments = call.getArguments();
    if (arguments.length != 1) {
      return false;
    }
    if (!(arguments[0] instanceof JSLiteralExpression)) {
      return false;
    }
    JSLiteralExpression literal = (JSLiteralExpression) arguments[0];
    return Objects.equals(literal.getStringValue(), "goog.testing.testSuite");
  }

  private static String getTestFilter(PsiFile file) {
    Project project = file.getProject();
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    WorkspacePath path = root.workspacePathFor(file.getVirtualFile());
    return '^'
        + root.directory().getName()
        + '/'
        + FileUtil.getNameWithoutExtension(path.relativePath())
        + '$';
  }
}
