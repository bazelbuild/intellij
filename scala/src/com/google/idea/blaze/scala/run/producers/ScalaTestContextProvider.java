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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link TestContextProvider} for run configurations related to Scala test classes (not handled by
 * JUnit) in Blaze. Handles only {@link ScalaTestTestFramework}.
 */
class ScalaTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    ClassAndTestNames classAndTestNames = getClassAndTestNames(context);

    if (classAndTestNames == null) {
      return null;
    }

    ScTypeDefinition testClass = classAndTestNames.testClass;

    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(
            testClass, TestSizeFinder.getTestSize(testClass));
    if (target == null) {
      return null;
    }

    return TestContext.builder(testClass, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .addBlazeFlagsModification(new ScalatestTestSelectorFlagsModification(testClass.getQualifiedName(), classAndTestNames.testNames))
        .setDescription(testClass.getName())
        .build();
  }

  private static class ClassAndTestNames {
    private final ScTypeDefinition testClass;
    /**
     * If set, contains a newline delimited list of test names.
     */
    @Nullable
    private final String testNames;

    public ClassAndTestNames(ScTypeDefinition testClass, @Nullable String testNames) {
      this.testClass = testClass;
      this.testNames = testNames;
    }
  }

  @Nullable
  private static ClassAndTestNames getClassAndTestNames(ConfigurationContext context) {
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
        .map(classWithTestName -> new ClassAndTestNames(classWithTestName.testClass(), classWithTestName.testName().getOrElse(() -> null)))
        .getOrElse(() -> null);
  }

  public static class ScalatestTestSelectorFlagsModification implements TestContext.BlazeFlagsModification {

    private static final String testClassSelectorFlag = BlazeFlags.TEST_ARG + "-s";

    private static final String testNameSelectorFlag = BlazeFlags.TEST_ARG + "-t";

    private final List<String> testNames;

    private final List<String> testNameFlags;

    private final String testClassFlag;

    public ScalatestTestSelectorFlagsModification(String testClassFqn, @Nullable String testNames) {
      if (testNames != null) {
        this.testNames = Arrays.stream(testNames.split("\n"))
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());
        this.testNameFlags = this.testNames.stream()
            // Scalatest names can contain spaces, so the name needs to be quoted
            // This means we need to escape " in the test name
            .map(name -> name.replace("\"", "\\\""))
            .map(escapedName -> BlazeFlags.TEST_ARG + "\"" + escapedName + "\"")
            .collect(Collectors.toList());
      } else {
        this.testNames = Collections.emptyList();
        this.testNameFlags = Collections.emptyList();
      }
      testClassFlag = BlazeFlags.TEST_ARG + testClassFqn;
    }

    private boolean flagsMatchSettings(List<String> flags) {
      int classSelectorIndex = flags.indexOf(testClassSelectorFlag);
      if (classSelectorIndex == -1) {
        return false;
      }
      int expectedSize = 2 + 2 * testNames.size();
      if (flags.size() < classSelectorIndex + expectedSize) {
        return false;
      }
      String classSelector = flags.get(classSelectorIndex);
      String clazz = flags.get(classSelectorIndex + 1);

      boolean matchesClassFlags = classSelector.equals(testClassSelectorFlag) && clazz.equals(testClassFlag);

      if (!matchesClassFlags) {
        return false;
      }

      // The test name flags we expect should be present
      int firstNameSelectorIndex = classSelectorIndex + 2;
      for (int i = 0; i < testNameFlags.size(); i++) {
        int nameSelectorIndex = firstNameSelectorIndex + i * 2;
        String nameSelectorFlag = flags.get(nameSelectorIndex);
        String nameFlag = flags.get(nameSelectorIndex + 1);
        boolean matchesNameFlags = nameSelectorFlag.equals(testNameSelectorFlag) &&
            nameFlag.equals(testNameFlags.get(i));
        if (!matchesNameFlags) {
          return false;
        }
      }

      // No test name flags we don't expect should be present after the class name
      long testSelectorCount = flags.stream()
          .skip(firstNameSelectorIndex)
          .filter(flag -> flag.equals(testNameSelectorFlag))
          .count();

      return testSelectorCount == testNames.size();
    }

    @Override
    public void modifyFlags(List<String> flags) {
      // Ordering matters here. ScalaTest interprets "-s className -t testName" to mean "run the test in the selected class with the selected name".
      // The reverse ordering "-t testName -s className" means "run all tests with the given name, and also all tests in the given class".
      if(!flagsMatchSettings(flags)) {
        flags.add(testClassSelectorFlag);
        flags.add(testClassFlag);
        for (String testNameFlag : testNameFlags) {
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
