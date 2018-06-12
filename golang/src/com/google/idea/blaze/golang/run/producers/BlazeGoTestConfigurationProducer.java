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
package com.google.idea.blaze.golang.run.producers;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionOrMethodDeclaration;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.sdkcompat.golang.GoTestFinderCompatUtils;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Producer for go_test run configurations. */
public class BlazeGoTestConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeGoTestConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  private static class TestLocation {
    private final TargetInfo target;
    private final GoFile file;
    @Nullable private final GoFunctionOrMethodDeclaration function;

    private TestLocation(
        TargetInfo target, GoFile file, @Nullable GoFunctionOrMethodDeclaration function) {
      this.target = target;
      this.file = file;
      this.function = function;
    }

    @Nullable
    private String testFilter() {
      return function != null ? BlazeFlags.TEST_FILTER + "=^" + function.getName() + "$" : null;
    }

    private String targetString() {
      return function != null
          ? String.format("%s (%s)", function.getName(), target.label.toString())
          : BlazeConfigurationNameBuilder.getTextForLabel(target.label);
    }

    PsiElement sourceElement() {
      return function != null ? function : file;
    }
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    TestLocation testLocation = testLocation(context);
    if (testLocation == null) {
      return false;
    }
    configuration.setTargetInfo(testLocation.target);
    sourceElement.set(testLocation.sourceElement());
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

    // remove conflicting flags from initial configuration
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    String testFilter = testLocation.testFilter();
    if (testFilter != null) {
      flags.add(testFilter);
    }
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(testLocation.targetString());
    configuration.setName(nameBuilder.build());
    configuration.setNameChangedByUser(true); // don't revert to generated name
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null
        || !BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
      return false;
    }
    TestLocation testlocation = testLocation(context);
    return testlocation != null
        && testlocation.target.label.equals(configuration.getTarget())
        && Objects.equals(handlerState.getTestFilterFlag(), testlocation.testFilter());
  }

  @Nullable
  private static TestLocation testLocation(ConfigurationContext context) {
    // Handled by SM runner.
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      return null;
    }
    PsiElement element = context.getPsiLocation();
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (!(file instanceof GoFile) || !GoTestFinderCompatUtils.isTestFile(file)) {
      return null;
    }
    TargetInfo testTarget = TestTargetHeuristic.testTargetForPsiElement(element);
    return testTarget != null
        ? new TestLocation(
            testTarget, (GoFile) file, GoTestFinderCompatUtils.findTestFunctionInContext(element))
        : null;
  }
}
