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

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.run.BlazeRunConfigurationFactory;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
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

  static class BlazeIntellijPluginRunConfigurationFactory extends BlazeRunConfigurationFactory {
    @Override
    public boolean handlesTarget(Project project, BlazeProjectData blazeProjectData, Label label) {
      if (!blazeProjectData
          .getWorkspaceLanguageSettings()
          .isWorkspaceType(WorkspaceType.INTELLIJ_PLUGIN)) {
        return false;
      }
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(TargetKey.forPlainTarget(label));
      return target != null && IntellijPluginRule.isPluginTarget(target);
    }

    @Override
    protected ConfigurationFactory getConfigurationFactory() {
      return getInstance().factory;
    }

    @Override
    public void setupConfiguration(RunConfiguration configuration, Label target) {
      final BlazeIntellijPluginConfiguration pluginConfig =
          (BlazeIntellijPluginConfiguration) configuration;
      getInstance().factory.setupConfigurationForRule(pluginConfig, target);
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

    private BlazeIntellijPluginConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    @Override
    public String getId() {
      // must be left unchanged for backwards compatibility
      return getName();
    }

    @Override
    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public BlazeIntellijPluginConfiguration createTemplateConfiguration(Project project) {

      BlazeIntellijPluginConfiguration config =
          new BlazeIntellijPluginConfiguration(
              project, this, "Unnamed", findExamplePluginTarget(project));
      config.vmParameters = currentVmOptions.getValue();
      return config;
    }

    private static Label findExamplePluginTarget(Project project) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        return null;
      }
      return projectData.getTargetMap().targets().stream()
          .filter(IntellijPluginRule::isPluginTarget)
          .map(TargetIdeInfo::getKey)
          .map(TargetKey::getLabel)
          .findFirst()
          .orElse(null);
    }

    @Override
    // Super method uses raw BeforeRunTask. Check super method again after #api202.
    @SuppressWarnings("rawtypes")
    public void configureBeforeRunTaskDefaults(
        Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      task.setEnabled(providerID.equals(BuildPluginBeforeRunTaskProvider.ID));
    }

    void setupConfigurationForRule(BlazeIntellijPluginConfiguration configuration, Label target) {
      configuration.setTarget(target);
      configuration.setGeneratedName();
      if (configuration.vmParameters == null) {
        configuration.vmParameters = currentVmOptions.getValue();
      }
      Sdk projectSdk = ProjectRootManager.getInstance(configuration.getProject()).getProjectSdk();
      if (IdeaJdkHelper.isIdeaJdk(projectSdk)) {
        configuration.setPluginSdk(projectSdk);
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
      vmoptions = vmoptions.replaceAll("#.*", "").replaceAll("\\s+", " ").trim();
      String vmoptionsFile = System.getProperty("jb.vmOptionsFile");
      if (vmoptionsFile != null) {
        vmoptions += String.format(" -Djb.vmOptionsFile=\"%s\"", vmoptionsFile);
      }
      vmoptions += " -Didea.is.internal=true";
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
