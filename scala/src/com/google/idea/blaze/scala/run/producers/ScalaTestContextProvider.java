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
          //This causes -t to be added to the Scalatest runner, selecting the relevant test case
          //Setting -s as well would cause the entire test class to run, so leave the test filter unset.
          .addBlazeFlagsModification(setScalatestSingleTestNameFlag(classAndTestName.testName))
          .build();
    } else {
      return builder
          //This causes -s to be added to the Scalatest runner, selecting the relevant class
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
    ScalaTestConfigurationProducer scalaTestConfigurationProducer = new ScalaTestConfigurationProducer();
    return scalaTestConfigurationProducer.getTestClassWithTestName(location)
        .map(classWithTestName -> new ClassAndTestName(classWithTestName.testClass(), classWithTestName.testName().getOrElse(() -> null)))
        .getOrElse(() -> null);
  }

  static TestContext.BlazeFlagsModification setScalatestSingleTestNameFlag(String testName) {
    String testSelectorFlag = BlazeFlags.TEST_ARG + "-t";
    //Scalatest names can contain spaces, so the name needs to be quoted
    String testNameFlag = BlazeFlags.TEST_ARG + "\"" + testName + "\"";
    return new TestContext.BlazeFlagsModification() {
      @Override
      public void modifyFlags(List<String> flags) {
        if (!flags.contains(testSelectorFlag) && !flags.contains(testNameFlag)) {
          flags.add(testSelectorFlag);
          flags.add(testNameFlag);
        }
      }

      @Override
      public boolean matchesConfigState(RunConfigurationFlagsState state) {
        List<String> rawFlags = state.getRawFlags();
        return rawFlags.contains(testSelectorFlag) && rawFlags.contains(testNameFlag);
      }
    };
  }

  private static String getTestFilter(PsiClass testClass) {
    // TODO: may need to append '#' if implementation changes.
    // https://github.com/bazelbuild/rules_scala/pull/216
    return testClass.getQualifiedName();
  }
}
