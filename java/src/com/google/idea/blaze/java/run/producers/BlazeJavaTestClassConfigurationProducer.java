/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Producer for run configurations related to Java test classes in Blaze. */
public class BlazeJavaTestClassConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeJavaTestClassConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  private static class TestLocation {
    final PsiClass testClass;
    final TargetInfo blazeTarget;

    private TestLocation(PsiClass testClass, TargetInfo blazeTarget) {
      this.testClass = testClass;
      this.blazeTarget = blazeTarget;
    }
  }

  /**
   * Returns the {@link TestLocation} corresponding to the single selected JUnit test class, or
   * {@code null} if something else is selected.
   */
  @Nullable
  private TestLocation getSingleJUnitTestClass(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }

    // check for contexts handled by a different producer
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      return null;
    }
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return null;
    }
    if (JavaTestCaseIdentifier.isAnyTestCase(context)) {
      return null;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null || testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }

    TestSize testSize = TestSizeFinder.getTestSize(testClass);
    TargetInfo target = RunUtil.targetForTestClass(testClass, testSize);
    return target != null ? new TestLocation(testClass, target) : null;
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    TestLocation location = getSingleJUnitTestClass(context);
    if (location == null) {
      return false;
    }
    sourceElement.set(location.testClass);
    configuration.setTargetInfo(location.blazeTarget);

    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    String testFilter = getTestFilter(location.testClass);
    if (testFilter == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(BlazeFlags.TEST_FILTER + "=" + testFilter);
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    String name =
        new BlazeConfigurationNameBuilder(configuration)
            .setTargetString(location.testClass.getName())
            .build();
    configuration.setName(name);
    configuration.setNameChangedByUser(true); // don't revert to generated name
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    TestLocation location = getSingleJUnitTestClass(context);
    if (location == null) {
      return false;
    }
    return checkIfAttributesAreTheSame(configuration, location);
  }

  private boolean checkIfAttributesAreTheSame(
      BlazeCommandRunConfiguration configuration, TestLocation location) {
    if (!location.blazeTarget.label.equals(configuration.getTarget())) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    String filter = getTestFilter(location.testClass);
    return filter != null
        && Objects.equals(BlazeFlags.TEST_FILTER + "=" + filter, handlerState.getTestFilterFlag());
  }

  @Nullable
  private static String getTestFilter(PsiClass testClass) {
    Set<PsiClass> innerTestClasses = ProducerUtils.getInnerTestClasses(testClass);
    if (innerTestClasses.isEmpty()) {
      return BlazeJUnitTestFilterFlags.testFilterForClass(testClass);
    }
    innerTestClasses.add(testClass);
    Map<PsiClass, Collection<Location<?>>> methodsPerClass =
        innerTestClasses.stream().collect(Collectors.toMap(c -> c, c -> ImmutableList.of()));
    return BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(methodsPerClass);
  }

  /**
   * Extension point to check if we're in a test case, and prevent generating a test class
   * configuration if we are.
   */
  public interface JavaTestCaseIdentifier {
    ExtensionPointName<JavaTestCaseIdentifier> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.JavaTestCaseIdentifier");

    static boolean isAnyTestCase(ConfigurationContext context) {
      return Arrays.stream(EP_NAME.getExtensions()).anyMatch(p -> p.isTestCase(context));
    }

    boolean isTestCase(ConfigurationContext context);
  }
}
