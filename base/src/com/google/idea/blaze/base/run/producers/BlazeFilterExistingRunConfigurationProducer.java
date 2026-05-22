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
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles the specific case where the user creates a run configuration by selecting test suites / classes / methods
 * from the test UI tree.
 *
 * <p>In this special case we already know the blaze target string, and only need to apply a filter
 * to the existing configuration. Delegates language-specific filter calculation to {@link BlazeTestEventsHandler}.
 */
public class BlazeFilterExistingRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeFilterExistingRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement
  ) {
    final var testFilterFlag = getTestFilter(context);
    if (testFilterFlag.isEmpty()) {
      return false;
    }

    final var handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null || !BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
      return false;
    }

    handlerState.getTestFilterState().setTestFilter(testFilterFlag.get());
    configuration.setName(configuration.getName() + " (filtered)");
    configuration.setNameChangedByUser(true);

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    final var testFilterFlag = getTestFilter(context);
    if (testFilterFlag.isEmpty()) {
      return false;
    }

    final var handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);

    return handlerState != null
        && Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)
        && Objects.equals(testFilterFlag.get(), handlerState.getTestFilterState().getTestFilter());
  }

  private static Optional<String> getTestFilter(ConfigurationContext context) {
    final var base = context.getOriginalConfiguration(null);
    if (!(base instanceof BlazeCommandRunConfiguration)) {
      return Optional.empty();
    }

    final var targets = ((BlazeCommandRunConfiguration) base).getTargets();
    if (targets.isEmpty()) {
      return Optional.empty();
    }

    final var selectedElements = SmRunnerUtils.getSelectedSmRunnerTreeElements(context);
    if (selectedElements.isEmpty()) {
      return Optional.empty();
    }

    return BlazeTestEventsHandler.getHandlerForTargets(context.getProject(), targets)
        .map(handler -> handler.getTestFilter(context.getProject(), selectedElements))
        .map(BlazeParametersListUtil::decodeTestFilterFlag);
  }
}
