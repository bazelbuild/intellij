/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import javax.annotation.Nullable;

/** Creates run configurations from a BUILD file targets.
 *  Based on BlazeBuildFileRunConfigurationProducer.java
 * */
public class BlazeBuildTargetRunConfigurationProducer
        extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  record BuildTarget(FuncallExpression rule, RuleType ruleType, Label label) {
    @Nullable
      TargetInfo guessTargetInfo() {
        String ruleName = rule.getFunctionName();
        if (ruleName == null) {
          return null;
        }
        Kind kind = Kind.fromRuleName(ruleName);
        return kind != null ? TargetInfo.builder(label, kind.getKindString()).build() : null;
      }
    }

  public BlazeBuildTargetRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
          BlazeCommandRunConfiguration configuration,
          ConfigurationContext context,
          Ref<PsiElement> sourceElement) {
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    // With query sync we don't need a sync to run a configuration
    if (blazeProjectData == null && Blaze.getProjectType(project) != ProjectType.QUERY_SYNC) {
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
    return Objects.equals(configuration.getTargets(), ImmutableList.of(target.label));
  }

  @Nullable
  private static BuildTarget getBuildTarget(ConfigurationContext context) {
    return getBuildTarget(
            PsiTreeUtil.getNonStrictParentOfType(context.getPsiLocation(), FuncallExpression.class));
  }

  @Nullable
  static BuildTarget getBuildTarget(@Nullable FuncallExpression rule) {
    if (rule == null) {
      return null;
    }
    String ruleType = rule.getFunctionName();
    Label label = rule.resolveBuildLabel();
    if (ruleType == null || label == null) {
      return null;
    }
    return new BuildTarget(rule, Kind.guessRuleType(ruleType), label);
  }

  private static void setupConfiguration(
          Project ignoredProject,
          BlazeProjectData ignoredBlazeProjectData,
          BlazeCommandRunConfiguration configuration,
          BuildTarget target) {
    TargetInfo info = target.guessTargetInfo();
    if (info != null) {
      configuration.setTargetInfo(info);
    } else {
      configuration.setTarget(target.label);
    }
    BlazeCommandRunConfigurationCommonState state =
            configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (state != null) {
      state.getCommandState().setCommand(BlazeCommandName.BUILD);
    }
    configuration.setGeneratedName();
  }

}
