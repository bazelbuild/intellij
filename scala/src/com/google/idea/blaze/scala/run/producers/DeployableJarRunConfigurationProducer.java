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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Optional;

import static com.google.idea.blaze.scala.run.producers.BlazeScalaMainClassRunConfigurationProducer.getMainObject;

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
    configuration.setMainClassName(mainObject.getTruncedQualifiedName());
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

    return mainClass.getQualifiedName().equals(mainObject.getTruncedQualifiedName())
        && configuration.getVMParameters().endsWith(
                String.format("%s_deploy.jar", target.label.targetName()));
  }

  @Nullable
  private static TargetInfo findTarget(Project project, ScObject mainObject) {
    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return null;
    }

    Collection<TargetInfo> targets =
            SourceToTargetFinder.findTargetsForSourceFile(
                    project, mainObjectFile, Optional.of(RuleType.LIBRARY));

    return Iterables.getFirst(targets, null);
  }

  private void setDeployableJarGeneratorTask(RunConfigurationBase config) {
    Project project = config.getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    runManager.setBeforeRunTasks(
        config, ImmutableList.of(new GenerateDeployableJarTaskProvider.Task()));
  }
}
