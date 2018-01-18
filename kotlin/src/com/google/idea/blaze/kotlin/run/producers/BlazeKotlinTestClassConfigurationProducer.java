/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;


public class BlazeKotlinTestClassConfigurationProducer extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {
    public BlazeKotlinTestClassConfigurationProducer() {
        super(BlazeCommandRunConfigurationType.getInstance());
    }

    @Override
    protected boolean doSetupConfigFromContext(
            BlazeCommandRunConfiguration configuration,
            ConfigurationContext context,
            Ref<PsiElement> sourceElement) {
        KotlinTestConfigBuilder configBuilder =
                KotlinTestConfigBuilder.fromContext(
                        context,
                        configuration,
                        BlazeCommandName.TEST);
        return configBuilder != null &&
                configBuilder.trySetSourceElement(sourceElement) != null &&
                configBuilder.trySetTargetInfo() != null &&
                configBuilder.tryUpdateHandlerState() != null;
    }

    @Override
    protected boolean doIsConfigFromContext(BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
        KotlinTestConfigBuilder configurationBuilder = KotlinTestConfigBuilder.fromContext(context, configuration, BlazeCommandName.TEST);
        return configurationBuilder != null && configurationBuilder.shouldConfigure();
    }
}
