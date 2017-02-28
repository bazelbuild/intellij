/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.clwb.run.test.GoogleTestLocation;
import com.google.idea.blaze.clwb.run.test.GoogleTestSpecification;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import java.util.Objects;
import javax.annotation.Nullable;

/** Producer for run configurations related to C/C++ test classes in Blaze. */
public class BlazeCidrTestConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeCidrTestConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  /** The single selected {@link PsiElement}. Returns null if multiple elements are selected. */
  @Nullable
  private static PsiElement selectedPsiElement(ConfigurationContext context) {
    PsiElement[] psi = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext());
    if (psi != null && psi.length > 1) {
      return null; // multiple elements selected.
    }
    Location<?> location = context.getLocation();
    return location != null ? location.getPsiElement() : null;
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {

    PsiElement element = selectedPsiElement(context);
    if (element == null) {
      return false;
    }
    GoogleTestLocation test = GoogleTestLocation.findGoogleTest(element);
    if (test == null) {
      return false;
    }
    Label label = getTestTarget(test.getPsiElement());
    if (label == null) {
      return false;
    }
    sourceElement.set(test.getPsiElement());
    configuration.setTarget(label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.setCommand(BlazeCommandName.TEST);

    ImmutableList.Builder<String> flags = ImmutableList.builder();
    String testFilter = test.getTestFilterFlag();
    if (testFilter != null) {
      flags.add(testFilter);
    }
    flags.addAll(handlerState.getBlazeFlags());

    handlerState.setBlazeFlags(flags.build());
    configuration.setName(
        String.format(
            "%s test: %s",
            Blaze.buildSystemName(configuration.getProject()), getTestName(label, test.gtest)));
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    PsiElement element = selectedPsiElement(context);
    if (element == null) {
      return false;
    }
    GoogleTestLocation test = GoogleTestLocation.findGoogleTest(element);
    if (test == null) {
      return false;
    }
    Label label = getTestTarget(test.getPsiElement());
    if (label == null) {
      return false;
    }
    return label.equals(configuration.getTarget())
        && Objects.equals(handlerState.getTestFilterFlag(), test.getTestFilterFlag());
  }

  @Nullable
  private static Label getTestTarget(PsiElement element) {
    return TestTargetHeuristic.testTargetForPsiElement(element);
  }

  private static String getTestName(Label target, GoogleTestSpecification gtest) {
    String filterDescription = gtest.description();
    return filterDescription != null
        ? String.format("%s (%s)", filterDescription, target.toString())
        : target.toString();
  }
}
