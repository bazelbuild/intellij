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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NullUtils;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for Blaze test run configuration producers.
 */
public abstract class BlazeTestRunConfigurationProducer<T extends RunConfiguration> extends RunConfigurationProducer<T> {

  protected BlazeTestRunConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return Blaze.isBlazeProject(self.getConfiguration().getProject());
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext self, ConfigurationFromContext other) {
    return Blaze.isBlazeProject(self.getConfiguration().getProject()) &&
           !other.isProducedBy(BlazeTestRunConfigurationProducer.class);
  }

  @Override
  protected final boolean setupConfigurationFromContext(T configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    if (NullUtils.hasNull(configuration, context, sourceElement)) {
      return false;
    }
    if (!validContext(context)) {
      return false;
    }
    return doSetupConfigFromContext(configuration, context, sourceElement);
  }

  protected abstract boolean doSetupConfigFromContext(
    @NotNull T configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement);

  @Override
  public final boolean isConfigurationFromContext(T configuration, ConfigurationContext context) {
    if (NullUtils.hasNull(configuration, context)) {
      return false;
    }
    if (!validContext(context)) {
      return false;
    }
    return doIsConfigFromContext(configuration, context);
  }

  protected abstract boolean doIsConfigFromContext(
    @NotNull T configuration,
    @NotNull ConfigurationContext context);

  private static boolean validContext(@NotNull ConfigurationContext context) {
    Module module = context.getModule();
    if (module == null) {
      return false;
    }
    if (!isBlazeContext(context)) {
      return false;
    }
    return true;
  }

  private static boolean isBlazeContext(@NotNull ConfigurationContext context) {
    return Blaze.isBlazeProject(context.getProject());
  }

}
