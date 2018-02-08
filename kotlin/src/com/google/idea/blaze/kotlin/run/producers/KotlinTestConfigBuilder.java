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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.kotlin.run.BlazeKotlinPsiUtils;
import com.google.idea.blaze.kotlin.run.BlazeKotlinRunnable;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.psi.KtClass;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Private utility class for building Kotlin test configurations.
 */
final class KotlinTestConfigBuilder {
    public final BlazeCommandName commandName;
    public final BlazeCommandRunConfiguration configuration;
    public final ConfigurationContext context;
    public final BlazeKotlinRunnable testElement;
    public final RuleType ruleType;

    private KotlinTestConfigBuilder(
            ConfigurationContext context,
            BlazeCommandRunConfiguration configuration,
            BlazeCommandName commandName,
            RuleType ruleType,
            BlazeKotlinRunnable testElement
    ) {
        this.context = context;
        this.configuration = configuration;
        this.commandName = commandName;
        this.ruleType = ruleType;
        this.testElement = testElement;
    }

    @Nullable
    public static KotlinTestConfigBuilder fromContext(
            ConfigurationContext context,
            BlazeCommandRunConfiguration configuration,
            BlazeCommandName commandName
    ) {
        Location location = context.getLocation();

        if (location == null)
            return null;

        RuleType ruleType;
        if (commandName == BlazeCommandName.TEST) {
            ruleType = RuleType.TEST;
        } else {
            throw new IllegalArgumentException("unhandled rule type");
        }

        KotlinTestConfigBuilder ktRunConfigurationBuilder = new KotlinTestConfigBuilder(
                context,
                configuration,
                commandName,
                ruleType,
                BlazeKotlinPsiUtils.getRunnableFrom(location.getPsiElement())
        );
        return ktRunConfigurationBuilder.isValid() ? ktRunConfigurationBuilder : null;
    }

    boolean shouldConfigure() {
        KotlinTestConfigBuilder configurationBuilder =
                KotlinTestConfigBuilder.fromContext(context, configuration, commandName);
        if (configurationBuilder == null) {
            return false;
        }

        BlazeCommandRunConfigurationCommonState handlerState =
                configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
            return false;
        }

        if (!Objects.equals(handlerState.getCommandState().getCommand(), configurationBuilder.commandName)) {
            return false;
        }

        String existingFlags = handlerState.getTestFilterFlag();
        String wouldProposeFlags = BlazeKotlinPsiUtils.getTestFilterFlags(testElement);

        if (!Objects.equals(existingFlags, wouldProposeFlags)) {
            return false;
        }

        TargetInfo target = configurationBuilder.getSingleTarget();
        if (target == null) {
            return false;
        }
        TargetExpression existingTarget = configuration.getTarget();
        return existingTarget != null || Objects.equals(configuration.getTarget(), target.label);
    }

    public boolean isValid() {
        return !testElement.containerClasses.isEmpty();
    }

    @Nullable
    PsiElement trySetSourceElement(Ref<PsiElement> sourceElement) {
        PsiElement target = null;
        if (testElement.function != null) {
            target = testElement.function;
        } else if (testElement.containerClasses.size() > 0) {
            target = testElement.containerClasses.get(testElement.containerClasses.size() - 1);
        }
        sourceElement.set(target);
        return target;
    }

    private ImmutableList<TargetInfo> targetInfos() {
        KtClass firstClass = testElement.containerClasses.get(0);
        return ImmutableList.copyOf(SourceToTargetFinder.findTargetsForSourceFile(
                firstClass.getProject(),
                new File(firstClass.getContainingFile().getVirtualFile().getPath()),
                Optional.of(ruleType)
        ).iterator());
    }

    // TODO select a target if multiple.
    @Nullable
    private TargetInfo getSingleTarget() {
        ImmutableList<TargetInfo> targetInfos = targetInfos();
        return targetInfos.size() != 1 ? null : targetInfos.get(0);
    }

    @Nullable
    public TargetInfo trySetTargetInfo() {
        TargetInfo target = getSingleTarget();
        if (target == null) {
            return null;
        }
        configuration.setTargetInfo(target);
        return target;
    }


    @Nullable
    RunConfigurationState tryUpdateHandlerState() {
        BlazeCommandRunConfigurationCommonState handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
            return null;
        }
        handlerState.getCommandState().setCommand(commandName);

        List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
        flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
        String testFilterFlag = BlazeKotlinPsiUtils.getTestFilterFlags(testElement);
        flags.add(testFilterFlag);
        handlerState.getBlazeFlagsState().setRawFlags(flags);

        BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
        String displayName = BlazeKotlinPsiUtils.getSimpleDisplayName(testElement);
        nameBuilder.setTargetString(displayName);
        configuration.setName(nameBuilder.build());
        configuration.setNameChangedByUser(true);
        return handlerState;
    }
}