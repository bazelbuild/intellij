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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;

/**
 * A factory creating run configurations based on BUILD file targets. Runs last, as a fallback for
 * the case where no more specialized factory handles the target.
 */
public class BlazeBuildTargetRunConfigurationFactory extends BlazeRunConfigurationFactory {

  // The rule types we auto-create run configurations for during sync.
  private static final ImmutableSet<RuleType> HANDLED_RULE_TYPES =
      ImmutableSet.of(RuleType.TEST, RuleType.BINARY);

  @Override
  public boolean handlesTarget(Project project, BlazeProjectData blazeProjectData, Label label) {
    TargetIdeInfo target = blazeProjectData.targetMap.get(TargetKey.forPlainTarget(label));
    return target != null && HANDLED_RULE_TYPES.contains(target.kind.ruleType);
  }

  @Override
  protected ConfigurationFactory getConfigurationFactory() {
    return BlazeCommandRunConfigurationType.getInstance().getFactory();
  }

  @Override
  public void setupConfiguration(RunConfiguration configuration, Label target) {
    BlazeCommandRunConfiguration blazeConfig = (BlazeCommandRunConfiguration) configuration;
    blazeConfig.setTarget(target);

    BlazeCommandRunConfigurationCommonState state =
        blazeConfig.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    Kind kind = blazeConfig.getKindForTarget();
    if (state != null && kind != null) {
      state.getCommandState().setCommand(commandForRuleType(kind.ruleType));
    }
    blazeConfig.setGeneratedName();
  }

  private static BlazeCommandName commandForRuleType(RuleType ruleType) {
    switch (ruleType) {
      case BINARY:
        return BlazeCommandName.RUN;
      case TEST:
        return BlazeCommandName.TEST;
      default:
        return BlazeCommandName.BUILD;
    }
  }
}
