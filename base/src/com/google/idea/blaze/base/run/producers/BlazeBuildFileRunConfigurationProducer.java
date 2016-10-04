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
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeRuleConfigurationFactory;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import javax.annotation.Nullable;

/** Creates run configurations from a BUILD file targets. */
public class BlazeBuildFileRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static class BuildTarget {
    private final FuncallExpression rule;
    private final String ruleType;
    private final Label label;
    @Nullable private final RuleIdeInfo ruleIdeInfo;

    public BuildTarget(
        FuncallExpression rule, String ruleType, Label label, @Nullable RuleIdeInfo ruleIdeInfo) {
      this.rule = rule;
      this.ruleType = ruleType;
      this.label = label;
      this.ruleIdeInfo = ruleIdeInfo;
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
    WorkspaceLanguageSettings workspaceLanguageSettings =
        blazeProjectData.workspaceLanguageSettings;
    BuildTarget target = getBuildTarget(context);
    if (target == null) {
      return false;
    }
    sourceElement.set(target.rule);
    setupConfiguration(configuration, workspaceLanguageSettings, target);
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
    WorkspaceLanguageSettings workspaceLanguageSettings =
        blazeProjectData.workspaceLanguageSettings;
    BlazeCommandRunConfiguration generatedConfiguration =
        new BlazeCommandRunConfiguration(
            configuration.getProject(), configuration.getFactory(), configuration.getName());
    setupConfiguration(generatedConfiguration, workspaceLanguageSettings, target);

    // TODO This check should be removed once isTestRule is in a RuleFactory and
    // test rules' suggestedName is modified to account for test filter flags.
    if (isTestRule(target.ruleType)) {
      BlazeCommandGenericRunConfigurationHandler handler =
          configuration.getHandlerIfType(BlazeCommandGenericRunConfigurationHandler.class);
      if (handler != null && handler.getTestFilterFlag() != null) {
        return false;
      }
    }
    // End-TODO

    return Objects.equals(configuration.suggestedName(), generatedConfiguration.suggestedName())
        && Objects.equals(
            configuration.getHandler().getCommandName(),
            generatedConfiguration.getHandler().getCommandName());
  }

  @Nullable
  private static BuildTarget getBuildTarget(ConfigurationContext context) {
    FuncallExpression rule =
        PsiTreeUtil.getNonStrictParentOfType(context.getPsiLocation(), FuncallExpression.class);
    if (rule == null) {
      return null;
    }
    String ruleType = rule.getFunctionName();
    Label label = rule.resolveBuildLabel();
    if (ruleType == null || label == null) {
      return null;
    }
    RuleIdeInfo ruleIdeInfo = RuleFinder.getInstance().ruleForTarget(context.getProject(), label);
    return new BuildTarget(rule, ruleType, label, ruleIdeInfo);
  }

  private static void setupConfiguration(
      BlazeCommandRunConfiguration configuration,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BuildTarget target) {
    // First see if a BlazeRuleConfigurationFactory can give us a specialized setup.
    if (target.ruleIdeInfo != null) {
      for (BlazeRuleConfigurationFactory configurationFactory :
          BlazeRuleConfigurationFactory.EP_NAME.getExtensions()) {
        if (configurationFactory.handlesRule(workspaceLanguageSettings, target.ruleIdeInfo)
            && configurationFactory.handlesConfiguration(configuration)) {
          configurationFactory.setupConfiguration(configuration, target.ruleIdeInfo);
          return;
        }
      }
    }

    // If no factory exists, directly set up the configuration.
    configuration.setTarget(target.label);
    // Try to make it a 'blaze build' command, if applicable.
    BlazeCommandGenericRunConfigurationHandler handler =
        configuration.getHandlerIfType(BlazeCommandGenericRunConfigurationHandler.class);
    if (handler != null) {
      // TODO move the old test rule functionality to a BlazeRuleConfigurationFactory
      handler.setCommand(
          isTestRule(target.ruleType) ? BlazeCommandName.TEST : BlazeCommandName.BUILD);
    }
    configuration.setGeneratedName();
  }

  // TODO this functionality should be moved to a BlazeRuleConfigurationFactory
  private static boolean isTestRule(String ruleType) {
    return isTestSuite(ruleType) || ruleType.endsWith("_test");
  }

  private static boolean isTestSuite(String ruleType) {
    return "test_suite".equals(ruleType);
  }
}
