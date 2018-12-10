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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.runner.ScalaMainMethodUtil;
import scala.Option;

/** Creates run configurations for Scala main classes sourced by scala_binary targets. */
public class BlazeScalaMainClassRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static final String SCALA_BINARY_MAP_KEY = "BlazeScalaBinaryMap";

  public BlazeScalaMainClassRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    Option<PsiMethod> mainMethod = ScalaMainMethodUtil.findMainMethod(mainObject);
    if (mainMethod.isEmpty()) {
      sourceElement.set(mainObject);
    } else {
      sourceElement.set(mainMethod.get());
    }
    TargetIdeInfo target = getTarget(context.getProject(), mainObject);
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
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    TargetIdeInfo target = getTarget(context.getProject(), mainObject);
    return target != null && Objects.equals(configuration.getTarget(), target.getKey().getLabel());
  }

  @Nullable
  static ScObject getMainObject(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation());
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    if (!(element.getContainingFile() instanceof ScalaFile)) {
      return null;
    }
    if (!element.isPhysical()) {
      return null;
    }
    return getMainObjectFromElement(element);
  }

  @Nullable
  private static ScObject getMainObjectFromElement(PsiElement element) {
    for (; element != null; element = element.getParent()) {
      if (element instanceof ScObject) {
        ScObject obj = (ScObject) element;
        if (ScalaMainMethodUtil.hasMainMethod(obj)) {
          return obj;
        }
      } else if (element instanceof ScalaFile) {
        return getMainObjectFromFile((ScalaFile) element);
      }
    }
    return null;
  }

  @Nullable
  private static ScObject getMainObjectFromFile(ScalaFile file) {
    for (PsiClass aClass : file.getClasses()) {
      if (!(aClass instanceof ScObject)) {
        continue;
      }
      ScObject obj = (ScObject) aClass;
      if (ScalaMainMethodUtil.hasMainMethod(obj)) {
        // Potentially multiple matches, we'll pick the first one.
        // TODO: prefer class with same name as file?
        // TODO: skip if not main_class of a rule.
        return obj;
      }
    }
    return null;
  }

  @Nullable
  private static TargetIdeInfo getTarget(Project project, ScObject mainObject) {
    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return null;
    }
    Collection<TargetIdeInfo> scalaBinaryTargets = findScalaBinaryTargets(project, mainObjectFile);

    // Scala objects are basically singletons with a '$' appended to the class name.
    // The trunced qualified name removes the '$',
    // so it matches the main class specified in the scala_binary rule.
    String qualifiedName = mainObject.qualifiedName();

    if (qualifiedName == null) {
      // out of date psi element; just take the first match
      return Iterables.getFirst(scalaBinaryTargets, null);
    }

    // Can't use getName because of the '$'.
    String className = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);

    // first look for a matching main_class
    TargetIdeInfo match =
        scalaBinaryTargets.stream()
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
        scalaBinaryTargets.stream()
            .filter(target -> className.equals(target.getKey().getLabel().targetName().toString()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }
    return Iterables.getFirst(scalaBinaryTargets, null);
  }

  /** Returns all scala_binary targets reachable from the given source file. */
  static Collection<TargetIdeInfo> findScalaBinaryTargets(Project project, File mainClassFile) {
    FilteredTargetMap map =
        SyncCache.getInstance(project)
            .get(
                SCALA_BINARY_MAP_KEY,
                BlazeScalaMainClassRunConfigurationProducer::computeTargetMap);
    return map != null ? map.targetsForSourceFile(mainClassFile) : ImmutableList.of();
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
        project,
        projectData.getArtifactLocationDecoder(),
        projectData.getTargetMap(),
        target ->
            target.isPlainTarget()
                && target.getKind().getLanguageClass().equals(LanguageClass.SCALA)
                && target.getKind().getRuleType().equals(RuleType.BINARY));
  }
}
