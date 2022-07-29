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
        .addBlazeFlagsModification(setScalatestTestSelectorArguments(classAndTestName))
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

  static TestContext.BlazeFlagsModification setScalatestTestSelectorArguments(ClassAndTestName classAndTestName) {
    String testClassSelectorFlag = BlazeFlags.TEST_ARG + "-s";
    String testClassFlag = BlazeFlags.TEST_ARG + classAndTestName.testClass.getQualifiedName();
    String testNameSelectorFlag = BlazeFlags.TEST_ARG + "-t";
    // Scalatest names can contain spaces, so the name needs to be quoted
    String testNameFlag = BlazeFlags.TEST_ARG + "\"" + classAndTestName.testName + "\"";
    return new TestContext.BlazeFlagsModification() {
      private boolean hasTestName() {
        return classAndTestName.testName != null;
      }

      @Override
      public void modifyFlags(List<String> flags) {
        // Ordering matters here. ScalaTest interprets "-s className -t testName" to mean "run the test in the selected class with the selected name".
        // The reverse ordering "-t testName -s className" means "run all tests with the given name, and also all tests in the given class".
        if (!flags.contains(testClassSelectorFlag) &&
            !flags.contains(testClassFlag)) {
          flags.add(testClassSelectorFlag);
          flags.add(testClassFlag);
        }

        if (hasTestName()) {
          if (!flags.contains(testNameSelectorFlag) &&
              !flags.contains(testNameFlag)) {
            flags.add(testNameSelectorFlag);
            flags.add(testNameFlag);
          }
        }
      }

      @Override
      public boolean matchesConfigState(RunConfigurationFlagsState state) {
        List<String> rawFlags = state.getRawFlags();
        boolean testClassFlagsSet = rawFlags.contains(testClassSelectorFlag) && rawFlags.contains(testClassFlag);
        boolean relevantNameFlagsSet = !hasTestName() || (rawFlags.contains(testNameSelectorFlag) && rawFlags.contains(testNameFlag));
        return testClassFlagsSet && relevantNameFlagsSet;
      }
    };
  }
}
