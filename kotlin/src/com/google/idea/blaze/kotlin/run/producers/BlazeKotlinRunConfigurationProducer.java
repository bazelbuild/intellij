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
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer;
import org.jetbrains.kotlin.psi.KtDeclarationContainer;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BlazeKotlinRunConfigurationProducer extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {
    private static final String KOTLIN_BINARY_MAP_KEY = "BlazeKotlinBinaryMap";

    public BlazeKotlinRunConfigurationProducer() {
        super(BlazeCommandRunConfigurationType.getInstance());
    }

    @Override
    protected boolean doSetupConfigFromContext(BlazeCommandRunConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
        Location location = context.getLocation();
        if (location == null) {
            return false;
        }
        return getValidTargetIdeInfo(context, location).map(targetIdeInfo -> {
            BlazeCommandRunConfigurationCommonState handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
            if (handlerState == null) {
                return false;
            }
            handlerState.getCommandState().setCommand(BlazeCommandName.RUN);

            sourceElement.set(location.getPsiElement());
            configuration.setTargetInfo(targetIdeInfo.toTargetInfo());
            configuration.setGeneratedName();

            return true;
        }).orElse(false);
    }

    private static Optional<TargetIdeInfo> getValidTargetIdeInfo(ConfigurationContext context, Location location) {
        KtDeclarationContainer entryPointContainer = KotlinRunConfigurationProducer.Companion.getEntryPointContainer(location.getPsiElement());
        if (entryPointContainer == null) {
            return Optional.empty();
        }
        String startClassFqName = KotlinRunConfigurationProducer.Companion.getStartClassFqName(entryPointContainer);
        if (startClassFqName == null) {
            return Optional.empty();
        }

        PsiFile runnableContainingFile = location.getPsiElement().getContainingFile();
        Map<Label, TargetIdeInfo> kotlinBinaryTargets = findKotlinBinaryTargets(context.getProject());

        Collection<TargetInfo> candidates = SourceToTargetFinder.findTargetsForSourceFile(
                context.getProject(),
                new File(runnableContainingFile.getVirtualFile().getPath()),
                Optional.empty());

        return candidates.stream()
                .map(x -> kotlinBinaryTargets.get(x.label))
                .filter(targetIdeInfo ->
                        targetIdeInfo != null &&
                                targetIdeInfo.javaIdeInfo != null &&
                                Objects.equals(targetIdeInfo.javaIdeInfo.javaBinaryMainClass, startClassFqName))
                .findFirst();
    }

    /**
     * Cached map of candidate binary targets for kotlin. Includes both Java and Kotlin target types.
     */
    private static Map<Label, TargetIdeInfo> findKotlinBinaryTargets(Project project) {
        return SyncCache.getInstance(project).get(KOTLIN_BINARY_MAP_KEY, (proj, data) ->
                data.targetMap.targets().stream()
                        .filter(x -> x.kind == Kind.KOTLIN_BINARY || x.kind == Kind.JAVA_BINARY)
                        .collect(Collectors.toMap(x -> x.key.label, x -> x))
        );
    }

    @Override
    protected boolean doIsConfigFromContext(BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
        BlazeCommandRunConfigurationCommonState handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
            return false;
        }
        if (!Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.RUN)) {
            return false;
        }
        Location location = context.getLocation();
        if (location == null) {
            return false;
        }
        return getValidTargetIdeInfo(context, location)
                .map(x -> configuration.getTarget() != null && configuration.getTarget().equals(x.key.label))
                .orElse(false);
    }
}
