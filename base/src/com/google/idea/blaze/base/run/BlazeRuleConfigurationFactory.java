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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** A factory creating run configurations based on Blaze rules. */
public abstract class BlazeRuleConfigurationFactory {
  public static final ExtensionPointName<BlazeRuleConfigurationFactory> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.RuleConfigurationFactory");

  /** Returns whether this factory can handle a rule. */
  public abstract boolean handlesRule(
      WorkspaceLanguageSettings workspaceLanguageSettings, RuleIdeInfo rule);

  /**
   * Returns whether this factory can initialize a configuration. <br>
   * The default implementation simply checks that the configuration has the same {@link
   * com.intellij.execution.configurations.ConfigurationType} as the type of {@link
   * #getConfigurationFactory()}.
   */
  public boolean handlesConfiguration(RunConfiguration configuration) {
    return getConfigurationFactory().getType().equals(configuration.getType());
  }

  /** Constructs and initializes {@link RunnerAndConfigurationSettings} for the given rule. */
  public RunnerAndConfigurationSettings createForRule(
      Project project, RunManager runManager, RuleIdeInfo rule) {
    ConfigurationFactory factory = getConfigurationFactory();
    RunConfiguration configuration = factory.createTemplateConfiguration(project, runManager);
    setupConfiguration(configuration, rule);
    return runManager.createConfiguration(configuration, factory);
  }

  /** The factory used to create configurations. */
  protected abstract ConfigurationFactory getConfigurationFactory();

  /** Initialize the configuration for the given rule. */
  public abstract void setupConfiguration(RunConfiguration configuration, RuleIdeInfo rule);
}
