/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import com.google.idea.blaze.android.run.BlazeBeforeRunTaskProvider;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeRuleConfigurationFactory;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A type for Android application run configurations adapted specifically to run android_binary
 * targets.
 */
public class BlazeAndroidBinaryRunConfigurationType implements ConfigurationType {
  private final BlazeAndroidBinaryRunConfigurationFactory factory =
    new BlazeAndroidBinaryRunConfigurationFactory(this);

  public static class BlazeAndroidBinaryRuleConfigurationFactory implements BlazeRuleConfigurationFactory {
    @Override
    public boolean handlesRule(WorkspaceLanguageSettings workspaceLanguageSettings, @NotNull RuleIdeInfo rule) {
      return rule.kindIsOneOf(Kind.ANDROID_BINARY);
    }

    @Override
    @NotNull
    public RunnerAndConfigurationSettings createForRule(@NotNull RunManager runManager, @NotNull RuleIdeInfo rule) {
      return getInstance().factory.createForRule(runManager, rule);
    }
  }

  public static class BlazeAndroidBinaryRunConfigurationFactory
    extends ConfigurationFactory {

    protected BlazeAndroidBinaryRunConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public BlazeAndroidBinaryRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new BlazeAndroidBinaryRunConfiguration(project, this);
    }

    @Override
    public boolean canConfigurationBeSingleton() {
      return false;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public void configureBeforeRunTaskDefaults(
      Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      task.setEnabled(providerID.equals(BlazeBeforeRunTaskProvider.ID));
    }

    @NotNull
    public RunnerAndConfigurationSettings createForRule(@NotNull RunManager runManager, @NotNull RuleIdeInfo rule) {
      final RunnerAndConfigurationSettings settings =
        runManager.createRunConfiguration(rule.label.toString(), this);
      final BlazeAndroidBinaryRunConfiguration configuration =
        (BlazeAndroidBinaryRunConfiguration) settings.getConfiguration();
      configuration.setTarget(rule.label);
      return settings;
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
      return false;
    }
  }

  @NotNull
  public static BlazeAndroidBinaryRunConfigurationType getInstance() {
    return
      ConfigurationTypeUtil.findConfigurationType(BlazeAndroidBinaryRunConfigurationType.class);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return Blaze.defaultBuildSystemName() + " Android Binary";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Launch/debug configuration for android_binary rules";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Override
  @NotNull
  public String getId() {
    return "BlazeAndroidBinaryRunConfigurationType";
  }

  @Override
  public BlazeAndroidBinaryRunConfigurationFactory[] getConfigurationFactories() {
    return new BlazeAndroidBinaryRunConfigurationFactory[]{factory};
  }
}
