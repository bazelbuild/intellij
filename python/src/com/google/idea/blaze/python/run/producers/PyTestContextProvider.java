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
package com.google.idea.blaze.python.run.producers;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.python.run.PyTestUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Producer for run configurations related to python test classes in Blaze. */
class PyTestContextProvider implements TestContextProvider {

  @Override
  public boolean webTestCompatible() {
    return true;
  }

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement element = selectedPsiElement(context);
    if (element == null) {
      return null;
    }
    TestLocation testLocation = testLocation(element);
    if (testLocation == null) {
      return null;
    }
    ListenableFuture<TargetInfo> testTarget =
        TestTargetHeuristic.targetFutureForPsiElement(element, /* testSize= */ null);
    if (testTarget == null) {
      return null;
    }
    String filter = testLocation.testFilter();
    String description =
        filter != null
            ? String.format("%s (%s)", filter, testLocation.testFile.getName())
            : testLocation.testFile.getName();
    return TestContext.builder()
        .setTarget(testTarget)
        .setSourceElement(testLocation.sourceElement())
        .setTestFilter(filter)
        .setDescription(description)
        .build();
  }

  private static class TestLocation {
    private final PyFile testFile;
    @Nullable private final PyClass testClass;
    @Nullable private final PyFunction testFunction;

    private TestLocation(
        PyFile testFile, @Nullable PyClass testClass, @Nullable PyFunction testFunction) {
      this.testFile = testFile;
      this.testClass = testClass;
      this.testFunction = testFunction;
    }

    private String getTestFilterForParameters(String testBase, PyDecorator decorator) {
      PyArgumentList parameterizedArgumentList = decorator.getArgumentList();
      if (parameterizedArgumentList == null) {
        return testBase;
      }
      PyExpression[] arguments = parameterizedArgumentList.getArguments();
      if (arguments.length == 0) {
        return testBase;
      }

      ArrayList<String> parameterizedFilters = new ArrayList<>();
      for (int i = 0; i < arguments.length; ++i) {

        parameterizedFilters.add(testBase + i);
      }

      if (parameterizedFilters.isEmpty()) {
        return testBase;
      }

      return Joiner.on(" ").join(parameterizedFilters);
    }

    private String getTestFilterForNamedParameters(String testBase, PyDecorator decorator) {
      PyArgumentList parameterizedArgumentList = decorator.getArgumentList();
      if (parameterizedArgumentList == null) {
        return testBase;
      }
      PyExpression[] arguments = parameterizedArgumentList.getArguments();
      if (arguments.length == 0) {
        return testBase;
      }

      ArrayList<String> parameterizedFilters = new ArrayList<>();
      for (PyExpression argument : arguments) {
        if (argument instanceof PyDictLiteralExpression) {
          // can be defined as a dict, use the name from element 'testcase_name'
          PyDictLiteralExpression dictArgument = (PyDictLiteralExpression) argument;
          for (PyKeyValueExpression keyValueExpression : dictArgument.getElements()) {
            PyExpression key = keyValueExpression.getKey();
            PyExpression value = keyValueExpression.getValue();
            if (key instanceof PyStringLiteralExpression
                && value instanceof PyStringLiteralExpression) {
              PyStringLiteralExpression keyString = (PyStringLiteralExpression) key;
              PyStringLiteralExpression valueString = (PyStringLiteralExpression) value;
              if (keyString.getStringValue().equals("testcase_name")) {
                parameterizedFilters.add(testBase + valueString.getStringValue());
              }
            }
          }
        } else if (argument instanceof PyParenthesizedExpression) {
          // can be defined as a tuple, use the name from the 0th element
          PyExpression contained = ((PyParenthesizedExpression) argument).getContainedExpression();
          if (contained instanceof PyTupleExpression) {
            PyTupleExpression tupleArgument = (PyTupleExpression) contained;
            PyExpression[] tupleElements = tupleArgument.getElements();
            if (tupleElements.length > 0 && tupleElements[0] instanceof PyStringLiteralExpression) {
              PyStringLiteralExpression testcaseName = (PyStringLiteralExpression) tupleElements[0];
              parameterizedFilters.add(testBase + testcaseName.getStringValue());
            }
          }
        }
      }
      if (parameterizedFilters.isEmpty()) {
        return testBase;
      }
      return Joiner.on(" ").join(parameterizedFilters);
    }

    @Nullable
    private String testFilter() {
      if (testClass == null) {
        return null;
      }
      if (testFunction == null) {
        return testClass.getName();
      }
      String nonParameterizedTest = testClass.getName() + "." + testFunction.getName();

      PyDecoratorList decoratorList = testFunction.getDecoratorList();
      if (decoratorList == null) {
        return nonParameterizedTest;
      }

      PyDecorator parameterizedDecorator = decoratorList.findDecorator("parameterized.parameters");
      if (parameterizedDecorator != null) {
        return getTestFilterForParameters(nonParameterizedTest, parameterizedDecorator);
      }

      PyDecorator namedParameterizedDecorator =
          decoratorList.findDecorator("parameterized.named_parameters");
      if (namedParameterizedDecorator != null) {
        return getTestFilterForNamedParameters(nonParameterizedTest, namedParameterizedDecorator);
      }
      return nonParameterizedTest;
    }

    PsiElement sourceElement() {
      if (testFunction != null) {
        return testFunction;
      }
      return testClass != null ? testClass : testFile;
    }
  }

  /**
   * The single selected {@link PsiElement}. Returns null if we're in a SM runner tree UI context
   * (handled by a different producer).
   */
  @Nullable
  private static PsiElement selectedPsiElement(ConfigurationContext context) {
    List<Location<?>> selectedTestUiElements =
        SmRunnerUtils.getSelectedSmRunnerTreeElements(context);
    if (!selectedTestUiElements.isEmpty()) {
      return null;
    }
    Location<?> location = context.getLocation();
    return location != null ? location.getPsiElement() : null;
  }

  @Nullable
  private static TestLocation testLocation(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile) || !PyTestUtils.isTestFile((PyFile) file)) {
      return null;
    }
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (pyClass == null || !PyTestUtils.isTestClass(pyClass)) {
      return new TestLocation((PyFile) file, null, null);
    }
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (pyFunction != null && PyTestUtils.isTestFunction(pyFunction)) {
      return new TestLocation((PyFile) file, pyClass, pyFunction);
    }
    return new TestLocation((PyFile) file, pyClass, null);
  }
}
