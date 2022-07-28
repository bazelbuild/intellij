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
import com.intellij.psi.PsiClass;
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

    TestContext.Builder builder = TestContext.builder(testClass, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setDescription(testClass.getName());

    if (classAndTestName.testName != null) {
      return builder
          // ScalaTest can run a single test in a class by using the flags "-s className -t testName".
          // rules_scala supports selecting a test class by setting the "--test_filter=className" flag,
          // which is then turned into -s alongside other "--test_arg" flags.
          // Sadly, this -s gets appended to the flag list, rather than prepended, resulting in
          // "-t testName -s className". ScalaTest cares about order for these, and this ordering
          // is interpreted to mean "run all tests with the given name, and also all tests in the given class".
          .addBlazeFlagsModification(setScalatestSingleTestNameFlag(classAndTestName))
          .build();
    } else {
      return builder
          // rules_scala translates --test_filter=className to -s className for the ScalaTest runner.
          .setTestFilter(getTestFilter(testClass))
          .build();
    }
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

  static TestContext.BlazeFlagsModification setScalatestSingleTestNameFlag(ClassAndTestName classAndTestName) {
    String testClassSelectorFlag = BlazeFlags.TEST_ARG + "-s";
    String testClassFlag = BlazeFlags.TEST_ARG + "\"" + classAndTestName.testClass.getQualifiedName() + "\"";
    String testNameSelectorFlag = BlazeFlags.TEST_ARG + "-t";
    // Scalatest names can contain spaces, so the name needs to be quoted
    String testNameFlag = BlazeFlags.TEST_ARG + "\"" + classAndTestName.testName + "\"";
    return new TestContext.BlazeFlagsModification() {
      @Override
      public void modifyFlags(List<String> flags) {
        if (!flags.contains(testClassSelectorFlag) &&
            !flags.contains(testClassFlag) &&
            !flags.contains(testNameSelectorFlag) &&
            !flags.contains(testNameFlag)) {
          flags.add(testClassSelectorFlag);
          flags.add(testClassFlag);
          flags.add(testNameSelectorFlag);
          flags.add(testNameFlag);
        }
      }

      @Override
      public boolean matchesConfigState(RunConfigurationFlagsState state) {
        List<String> rawFlags = state.getRawFlags();
        return rawFlags.contains(testClassSelectorFlag) &&
            rawFlags.contains(testClassFlag) &&
            rawFlags.contains(testNameSelectorFlag) &&
            rawFlags.contains(testNameFlag);
      }
    };
  }

  private static String getTestFilter(PsiClass testClass) {
    // TODO: may need to append '#' if implementation changes.
    // https://github.com/bazelbuild/rules_scala/pull/216
    return testClass.getQualifiedName();
  }
}
