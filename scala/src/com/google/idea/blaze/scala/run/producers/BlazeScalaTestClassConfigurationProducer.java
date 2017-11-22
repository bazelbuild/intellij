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

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.TestSizeAnnotationMap;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework;

/**
 * Producer for run configurations related to Scala test classes (not handled by JUnit) in Blaze.
 * Handles only {@link ScalaTestTestFramework}. Other supported frameworks (junit and specs2) are
 * handled by {@link BlazeScalaJunitTestClassConfigurationProducer} and {@link
 * BlazeScalaSpecs2TestExprConfigurationProducer}, respectively.
 */
public class BlazeScalaTestClassConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeScalaTestClassConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    ScClass testClass = getTestClass(context);
    if (testClass == null) {
      return false;
    }
    sourceElement.set(testClass);
    TargetInfo target =
        RunUtil.targetForTestClass(testClass, TestSizeAnnotationMap.getTestSize(testClass));
    if (target == null) {
      return false;
    }
    configuration.setTarget(target.label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }

    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(getTestFilterFlag(testClass));
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);
    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(testClass.getName());
    configuration.setName(nameBuilder.build());
    configuration.setNameChangedByUser(true); // don't revert to generated name
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    PsiClass testClass = getTestClass(context);
    if (testClass == null) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null
        || !Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)
        || !Objects.equals(handlerState.getTestFilterFlag(), getTestFilterFlag(testClass))) {
      return false;
    }
    TargetInfo target =
        RunUtil.targetForTestClass(testClass, TestSizeAnnotationMap.getTestSize(testClass));
    return target != null && Objects.equals(configuration.getTarget(), target.label);
  }

  @Nullable
  private static ScClass getTestClass(ConfigurationContext context) {
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
    return getTestClass(location);
  }

  @Nullable
  private static ScClass getTestClass(Location<?> location) {
    PsiElement element = location.getPsiElement();
    ScClass testClass;
    if (element instanceof ScClass) {
      testClass = (ScClass) element;
    } else {
      testClass = PsiTreeUtil.getParentOfType(element, ScClass.class);
    }
    if (testClass != null && isTestClass(testClass)) {
      return testClass;
    }
    return null;
  }

  private static boolean isTestClass(ScClass testClass) {
    TestFramework framework = TestFrameworks.detectFramework(testClass);
    return framework instanceof ScalaTestTestFramework && framework.isTestClass(testClass);
  }

  private static String getTestFilterFlag(PsiClass testClass) {
    // TODO: may need to append '#' if implementation changes.
    // https://github.com/bazelbuild/rules_scala/pull/216
    return BlazeFlags.TEST_FILTER + "=" + testClass.getQualifiedName();
  }
}
