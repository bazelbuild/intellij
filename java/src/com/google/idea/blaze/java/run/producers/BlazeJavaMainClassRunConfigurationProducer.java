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
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nullable;

/** Creates run configurations for Java main classes sourced by java_binary targets. */
public class BlazeJavaMainClassRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static final String JAVA_BINARY_MAP_KEY = "BlazeJavaBinaryMap";

  public BlazeJavaMainClassRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    PsiClass mainClass = getMainClass(context);
    if (mainClass == null) {
      return false;
    }
    // Try setting source element to a main method so ApplicationConfigurationProducer
    // can't override our configuration by producing a more specific one.
    PsiMethod mainMethod = PsiMethodUtil.findMainMethod(mainClass);
    if (mainMethod == null) {
      sourceElement.set(mainClass);
    } else {
      sourceElement.set(mainMethod);
    }

    TargetIdeInfo target = getTarget(context.getProject(), mainClass);
    if (target == null) {
      return false;
    }
    configuration.setTargetInfo(target.toTargetInfo());
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.RUN);
    configuration.setGeneratedName();
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
    if (!Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.RUN)) {
      return false;
    }
    PsiClass mainClass = getMainClass(context);
    if (mainClass == null) {
      return false;
    }
    TargetIdeInfo target = getTarget(context.getProject(), mainClass);
    if (target == null) {
      return false;
    }
    return Objects.equals(configuration.getTarget(), target.getKey().getLabel());
  }

  @Nullable
  private static PsiClass getMainClass(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) {
      return null;
    }
    return ApplicationConfigurationType.getMainClass(element);
  }

  @Nullable
  private static TargetIdeInfo getTarget(Project project, PsiClass mainClass) {
    File mainClassFile = RunUtil.getFileForClass(mainClass);
    if (mainClassFile == null) {
      return null;
    }
    Collection<TargetIdeInfo> javaBinaryTargets = findJavaBinaryTargets(project, mainClassFile);

    String qualifiedName = mainClass.getQualifiedName();
    String className = mainClass.getName();
    if (qualifiedName == null || className == null) {
      // out of date psi element; just take the first match
      return Iterables.getFirst(javaBinaryTargets, null);
    }

    // first look for a matching main_class
    TargetIdeInfo match =
        javaBinaryTargets.stream()
            .filter(
                target ->
                    target.getJavaIdeInfo() != null
                        && qualifiedName.equals(target.getJavaIdeInfo().getJavaBinaryMainClass()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    match =
        javaBinaryTargets.stream()
            .filter(target -> className.equals(target.getKey().getLabel().targetName().toString()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }
    return Iterables.getFirst(javaBinaryTargets, null);
  }

  /** Returns all java_binary targets reachable from the given source file. */
  private static Collection<TargetIdeInfo> findJavaBinaryTargets(
      Project project, File mainClassFile) {
    FilteredTargetMap map =
        SyncCache.getInstance(project)
            .get(JAVA_BINARY_MAP_KEY, BlazeJavaMainClassRunConfigurationProducer::computeTargetMap);
    return map != null ? map.targetsForSourceFile(mainClassFile) : ImmutableList.of();
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
        project,
        projectData.getArtifactLocationDecoder(),
        projectData.getTargetMap(),
        target ->
            target.isPlainTarget()
                && target.getKind().getLanguageClass().equals(LanguageClass.JAVA)
                && target.getKind().getRuleType().equals(RuleType.BINARY));
  }
}
