/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.idea.blaze.scala.run.producers.ScalaBinaryContextProvider.getMainObject;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;

class DeployableJarRunConfigurationProducer
    extends BlazeRunConfigurationProducer<ApplicationConfiguration> {

  static final Key<Label> TARGET_LABEL = Key.create("blaze.scala.library.target.label");

  DeployableJarRunConfigurationProducer() {
    super(ApplicationConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      ApplicationConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    TargetInfo target = findTarget(context.getProject(), mainObject);
    if (target == null) {
      return false;
    }

    configuration.putUserData(TARGET_LABEL, target.label);
    configuration.setModule(context.getModule());
    configuration.setMainClassName(mainObject.qualifiedName());
    configuration.setName(mainObject.name());
    configuration.setNameChangedByUser(true); // don't revert to generated name

    setDeployableJarGeneratorTask(configuration);

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      ApplicationConfiguration configuration, ConfigurationContext context) {
    PsiClass mainClass = configuration.getMainClass();
    if (mainClass == null || mainClass.getQualifiedName() == null) {
      return false;
    }
    if (configuration.getVMParameters() == null) {
      return false;
    }
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    TargetInfo target = findTarget(context.getProject(), mainObject);
    if (target == null) {
      return false;
    }

    return mainClass.getQualifiedName().equals(mainObject.qualifiedName())
        && configuration
            .getVMParameters()
            .endsWith(String.format("%s_deploy.jar", target.label.targetName()));
  }

  @Nullable
  private static TargetInfo findTarget(Project project, ScObject mainObject) {
    if (Blaze.getBuildSystem(project) != BuildSystem.Bazel) {
      // disabled for blaze projects for performance reasons. If we want this for blaze projects,
      // first look at limiting search to direct deps of
      return null;
    }
    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return null;
    }
    return findScalaLibraryTarget(project, mainObjectFile);
  }

  /** Finds a jar-providing library target directly building the given source file. */
  @Nullable
  private static TargetInfo findScalaLibraryTarget(Project project, File sourceFile) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    return SourceToTargetMap.getInstance(project).getRulesForSourceFile(sourceFile).stream()
        .map(blazeProjectData.getTargetMap()::get)
        .filter(Objects::nonNull)
        .filter(t -> relevantTarget(t))
        .map(TargetIdeInfo::toTargetInfo)
        .findFirst()
        .orElse(null);
  }

  private static boolean relevantTarget(TargetIdeInfo target) {
    return target.isPlainTarget()
        && target.getKind().getRuleType().equals(RuleType.LIBRARY)
        && target.getJavaIdeInfo() != null;
  }

  private void setDeployableJarGeneratorTask(RunConfigurationBase config) {
    Project project = config.getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    runManager.setBeforeRunTasks(
        config, ImmutableList.of(new GenerateDeployableJarTaskProvider.Task()));
  }
}
