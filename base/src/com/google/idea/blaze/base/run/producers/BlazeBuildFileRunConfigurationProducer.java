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
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeRunConfigurationFactory;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import javax.annotation.Nullable;

/** Creates run configurations from a BUILD file targets. */
public class BlazeBuildFileRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static final Logger logger =
      Logger.getInstance(BlazeBuildFileRunConfigurationProducer.class);

  private static class BuildTarget {

    private final FuncallExpression rule;
    private final String ruleType;
    private final Label label;

    BuildTarget(FuncallExpression rule, String ruleType, Label label) {
      this.rule = rule;
      this.ruleType = ruleType;
      this.label = label;
    }
  }

  public BlazeBuildFileRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(configuration.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return false;
    }
    BuildTarget target = getBuildTarget(context);
    if (target == null) {
      return false;
    }
    sourceElement.set(target.rule);
    setupConfiguration(configuration.getProject(), blazeProjectData, configuration, target);
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BuildTarget target = getBuildTarget(context);
    if (target == null) {
      return false;
    }
    if (!Objects.equals(configuration.getTarget(), target.label)) {
      return false;
    }
    // We don't know any details about how the various factories set up configurations from here.
    // Simply returning true at this point would be overly broad
    // (all configs with a matching target would be identified).
    // A complete equality check, meanwhile, would be too restrictive
    // (things like config name and user flags shouldn't count)
    // - not to mention we lack the equals() implementations needed to perform such a check!

    // So we compromise: if the target, suggested name, and command name match,
    // we consider it close enough. The suggested name is checked because it tends
    // to cover what the handler considers important,
    // and ignores changes the user may have made to the name.
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(configuration.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return false;
    }
    BlazeCommandRunConfiguration generatedConfiguration =
        new BlazeCommandRunConfiguration(
            configuration.getProject(), configuration.getFactory(), configuration.getName());
    setupConfiguration(
        configuration.getProject(), blazeProjectData, generatedConfiguration, target);

    // ignore filtered test configs, produced by other configuration producers.
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState != null && handlerState.getTestFilterFlag() != null) {
      return false;
    }

    return Objects.equals(configuration.suggestedName(), generatedConfiguration.suggestedName())
        && Objects.equals(
            configuration.getHandler().getCommandName(),
            generatedConfiguration.getHandler().getCommandName());
  }

  public static boolean handlesTarget(Project project, Label label) {
    return buildTargetFromLabel(project, label) != null;
  }

  @Nullable
  private static BuildTarget buildTargetFromLabel(Project project, Label label) {
    PsiElement psiElement = BuildReferenceManager.getInstance(project).resolveLabel(label);
    if (!(psiElement instanceof FuncallExpression)) {
      return null;
    }
    return targetFromFuncall((FuncallExpression) psiElement);
  }

  @Nullable
  private static BuildTarget getBuildTarget(ConfigurationContext context) {
    return targetFromFuncall(
        PsiTreeUtil.getNonStrictParentOfType(context.getPsiLocation(), FuncallExpression.class));
  }

  @Nullable
  private static BuildTarget targetFromFuncall(@Nullable FuncallExpression rule) {
    if (rule == null) {
      return null;
    }
    String ruleType = rule.getFunctionName();
    Label label = rule.resolveBuildLabel();
    if (ruleType == null || label == null) {
      return null;
    }
    return new BuildTarget(rule, ruleType, label);
  }

  public static void setupConfiguration(RunConfiguration configuration, Label label) {
    BuildTarget target = buildTargetFromLabel(configuration.getProject(), label);
    if (target == null || !(configuration instanceof BlazeCommandRunConfiguration)) {
      logger.error("Configuration not handled by BUILD file config producer: " + configuration);
      return;
    }
    setupBuildFileConfiguration((BlazeCommandRunConfiguration) configuration, target);
  }

  private static void setupConfiguration(
      Project project,
      BlazeProjectData blazeProjectData,
      BlazeCommandRunConfiguration configuration,
      BuildTarget target) {
    // First see if a BlazeRunConfigurationFactory can give us a specialized setup.
    for (BlazeRunConfigurationFactory configurationFactory :
        BlazeRunConfigurationFactory.EP_NAME.getExtensions()) {
      if (configurationFactory.handlesTarget(project, blazeProjectData, target.label)
          && configurationFactory.handlesConfiguration(configuration)) {
        configurationFactory.setupConfiguration(configuration, target.label);
        return;
      }
    }

    // If no factory exists, directly set up the configuration.
    setupBuildFileConfiguration(configuration, target);
  }

  private static void setupBuildFileConfiguration(
      BlazeCommandRunConfiguration configuration, BuildTarget target) {
    configuration.setTarget(target.label);
    // Try to make it a 'blaze build' command, if applicable.
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState != null) {
      // TODO move the old test rule functionality to a BlazeRunConfigurationFactory
      BlazeCommandName command =
          Kind.isTestRule(target.ruleType) ? BlazeCommandName.TEST : BlazeCommandName.BUILD;
      handlerState.getCommandState().setCommand(command);
    }
    configuration.setGeneratedName();
  }
}
