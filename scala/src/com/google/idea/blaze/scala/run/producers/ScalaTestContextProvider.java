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
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.java.run.producers.TestSizeFinder;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer;
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@link TestContextProvider} for run configurations related to Scala test classes (not handled by
 * JUnit) in Blaze. Handles only {@link ScalaTestTestFramework}.
 */
class ScalaTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    ClassAndTestName classAndTestName = getTestClass(context);

    if (classAndTestName == null) {
      return null;
    }

    ScTypeDefinition testClass = classAndTestName.testClass;

    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(
            testClass, TestSizeFinder.getTestSize(testClass));
    if (target == null) {
      return null;
    }

    return TestContext.builder(testClass, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .addBlazeFlagsModification(new ScalatestTestSelectorFlagsModification(testClass.getQualifiedName(), classAndTestName.testName))
        .setDescription(testClass.getName())
        .build();
  }

  private static class ClassAndTestName {
    private final ScTypeDefinition testClass;
    @Nullable
    private final String testName;

    public ClassAndTestName(ScTypeDefinition testClass, @Nullable String testName) {
      this.testClass = testClass;
      this.testName = testName;
    }
  }

  @Nullable
  private static ClassAndTestName getTestClass(ConfigurationContext context) {
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
    ScalaTestConfigurationProducer scalaTestConfigurationProducer = ScalaTestConfigurationProducer.instance();
    return scalaTestConfigurationProducer.getTestClassWithTestName(location)
        .map(classWithTestName -> new ClassAndTestName(classWithTestName.testClass(), classWithTestName.testName().getOrElse(() -> null)))
        .getOrElse(() -> null);
  }

  public static class ScalatestTestSelectorFlagsModification implements TestContext.BlazeFlagsModification {
    @Nullable
    private final String testName;

    private final String testClassSelectorFlag;
    private final String testClassFlag;
    @Nullable
    private final String testNameSelectorFlag;
    @Nullable
    private final String testNameFlag;

    public ScalatestTestSelectorFlagsModification(String testClassFqn, @Nullable String testName) {
      this.testName = testName;
      testClassSelectorFlag = BlazeFlags.TEST_ARG + "-s";
      testClassFlag = BlazeFlags.TEST_ARG + testClassFqn;
      testNameSelectorFlag = BlazeFlags.TEST_ARG + "-t";
      if (testName != null) {
        // Scalatest names can contain spaces, so the name needs to be quoted
        // This means we need to escape " in the test name
        String escapedTestName = testName.replace("\"", "\\\"");
        testNameFlag = BlazeFlags.TEST_ARG + "\"" + escapedTestName + "\"";
      } else {
        testNameFlag = null;
      }
    }

    private boolean hasTestName() {
      return testName != null;
    }

    private boolean flagsMatchSettings(List<String> flags) {
      int classSelectorIndex = flags.indexOf(testClassSelectorFlag);
      if (classSelectorIndex == -1) {
        return false;
      }
      int expectedSize = hasTestName() ? 4 : 2;
      if (flags.size() < classSelectorIndex + expectedSize) {
        return false;
      }
      String classSelector = flags.get(classSelectorIndex);
      String clazz = flags.get(classSelectorIndex + 1);
      boolean matchesClassFlags = classSelector.equals(testClassSelectorFlag) && clazz.equals(testClassFlag);
      if (!hasTestName()) {
        boolean hasTestSelector = flags.contains(testNameSelectorFlag);
        return matchesClassFlags && !hasTestSelector;
      } else {
        String nameSelector = flags.get(classSelectorIndex + 2);
        String name = flags.get(classSelectorIndex + 3);
        boolean matchesNameFlags = nameSelector.equals(testNameSelectorFlag) &&
            name.equals(testNameFlag);
        return matchesClassFlags && matchesNameFlags;
      }
    }

    @Override
    public void modifyFlags(List<String> flags) {
      // Ordering matters here. ScalaTest interprets "-s className -t testName" to mean "run the test in the selected class with the selected name".
      // The reverse ordering "-t testName -s className" means "run all tests with the given name, and also all tests in the given class".
      if(!flagsMatchSettings(flags)) {
        flags.add(testClassSelectorFlag);
        flags.add(testClassFlag);
        if (hasTestName()) {
          flags.add(testNameSelectorFlag);
          flags.add(testNameFlag);
        }
      }
    }

    @Override
    public boolean matchesConfigState(RunConfigurationFlagsState state) {
      return flagsMatchSettings(state.getRawFlags());
    }
  }
}
