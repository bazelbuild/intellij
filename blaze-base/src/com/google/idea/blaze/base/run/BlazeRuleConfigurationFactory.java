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
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * A factory creating run configurations based on Blaze rules.
 */
public interface BlazeRuleConfigurationFactory {
  ExtensionPointName<BlazeRuleConfigurationFactory> EP_NAME =
    ExtensionPointName.create("com.google.idea.blaze.RuleConfigurationFactory");

  /**
   * Returns whether this factory can handle a rule.
   */
  boolean handlesRule(WorkspaceLanguageSettings workspaceLanguageSettings, RuleIdeInfo rule);

  /** Constructs and initializes a configuration for the given rule. */
  RunnerAndConfigurationSettings createForRule(RunManager runManager, RuleIdeInfo rule);
}
