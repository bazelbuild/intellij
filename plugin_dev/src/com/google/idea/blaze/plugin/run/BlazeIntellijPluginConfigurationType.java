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
import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyValue;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * A type for run configurations that build an IntelliJ plugin jar via blaze, then load them in an
 * IntelliJ application
 */
public class BlazeIntellijPluginConfigurationType implements ConfigurationType {

  private final BlazeIntellijPluginConfigurationFactory factory =
      new BlazeIntellijPluginConfigurationFactory(this);

  static class BlazeIntellijPluginRuleConfigurationFactory extends BlazeRuleConfigurationFactory {
    @Override
    public boolean handlesRule(
        WorkspaceLanguageSettings workspaceLanguageSettings, RuleIdeInfo rule) {
      return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.INTELLIJ_PLUGIN)
          && IntellijPluginRule.isPluginRule(rule);
    }

    @Override
    protected ConfigurationFactory getConfigurationFactory() {
      return getInstance().factory;
    }

    @Override
    public void setupConfiguration(RunConfiguration configuration, RuleIdeInfo rule) {
      final BlazeIntellijPluginConfiguration pluginConfig =
          (BlazeIntellijPluginConfiguration) configuration;
      getInstance().factory.setupConfigurationForRule(pluginConfig, rule);
    }
  }

  static class BlazeIntellijPluginConfigurationFactory extends ConfigurationFactory {

    private static NullableLazyValue<String> currentVmOptions =
        new NullableLazyValue<String>() {
          @Nullable
          @Override
          protected String compute() {
            return defaultVmOptions();
          }
        };

    protected BlazeIntellijPluginConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    @Override
    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public BlazeIntellijPluginConfiguration createTemplateConfiguration(Project project) {
      BlazeIntellijPluginConfiguration config =
          new BlazeIntellijPluginConfiguration(
              project,
              this,
              "Unnamed",
              RuleFinder.getInstance().findFirstRule(project, IntellijPluginRule::isPluginRule));
      config.vmParameters = currentVmOptions.getValue();
      return config;
    }

    @Override
    public void configureBeforeRunTaskDefaults(
        Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      task.setEnabled(providerID.equals(BuildPluginBeforeRunTaskProvider.ID));
    }

    void setupConfigurationForRule(
        BlazeIntellijPluginConfiguration configuration, RuleIdeInfo rule) {
      configuration.setTarget(rule.label);
      configuration.setGeneratedName();
      if (configuration.vmParameters == null) {
        configuration.vmParameters = currentVmOptions.getValue();
      }
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
      return true;
    }

    private static String defaultVmOptions() {
      String vmoptions = VMOptions.read();
      if (vmoptions == null) {
        return null;
      }
      vmoptions = vmoptions.replaceAll("\\s+", " ").trim();
      String vmoptionsFile = System.getProperty("jb.vmOptionsFile");
      if (vmoptionsFile != null) {
        vmoptions += " -Djb.vmOptionsFile=" + vmoptionsFile;
      }
      return vmoptions;
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
