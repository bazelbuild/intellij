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
package com.google.idea.blaze.plugin.run;

import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.run.BlazeRuleConfigurationFactory;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import javax.swing.*;

/**
 * A type for run configurations that build an IntelliJ plugin jar via blaze,
 * then load them in an IntelliJ application
 */
public class BlazeIntellijPluginConfigurationType implements ConfigurationType {

  private final BlazeIntellijPluginConfigurationFactory factory = new BlazeIntellijPluginConfigurationFactory(this);

  public static class BlazeIntellijPluginRuleConfigurationFactory implements BlazeRuleConfigurationFactory {
    @Override
    public boolean handlesRule(WorkspaceLanguageSettings workspaceLanguageSettings, RuleIdeInfo rule) {
      return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.INTELLIJ_PLUGIN)
             && IntellijPluginRule.isPluginRule(rule);
    }

    @Override
    public RunnerAndConfigurationSettings createForRule(RunManager runManager, RuleIdeInfo rule) {
      return getInstance().factory.createForRule(runManager, rule);
    }
  }

  public static class BlazeIntellijPluginConfigurationFactory extends ConfigurationFactory {

    protected BlazeIntellijPluginConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    @Override
    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public BlazeIntellijPluginConfiguration createTemplateConfiguration(Project project) {
      return new BlazeIntellijPluginConfiguration(
        project,
        this,
        "Unnamed",
        RuleFinder.getInstance().findFirstRule(project, IntellijPluginRule::isPluginRule)
      );
    }

    @Override
    public void configureBeforeRunTaskDefaults(
      Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      task.setEnabled(providerID.equals(BuildPluginBeforeRunTaskProvider.ID));
    }

    public RunnerAndConfigurationSettings createForRule(RunManager runManager, RuleIdeInfo rule) {
      final RunnerAndConfigurationSettings settings =
        runManager.createRunConfiguration(rule.label.toString(), this);
      final BlazeIntellijPluginConfiguration configuration =
        (BlazeIntellijPluginConfiguration) settings.getConfiguration();
      configuration.setTarget(rule.label);
      return settings;
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
      return true;
    }
  }

  public static BlazeIntellijPluginConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(BlazeIntellijPluginConfigurationType.class);
  }

  @Override
  public String getDisplayName() {
    return Blaze.defaultBuildSystemName() + " IntelliJ Plugin";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Configuration for launching an IntelliJ plugin in a sandbox environment.";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Plugin;
  }

  @Override
  public String getId() {
    return "BlazeIntellijPluginConfigurationType";
  }

  @Override
  public BlazeIntellijPluginConfigurationFactory[] getConfigurationFactories() {
    return new BlazeIntellijPluginConfigurationFactory[] {factory};
  }

  public BlazeIntellijPluginConfigurationFactory getFactory() {
    return factory;
  }

}
