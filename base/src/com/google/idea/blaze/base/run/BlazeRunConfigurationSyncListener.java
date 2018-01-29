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

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.RunConfigurationsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.run.exporter.RunConfigurationSerializer;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.transactions.Transactions;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Imports run configurations specified in the project view, and creates run configurations for
 * project view targets, where appropriate.
 */
public class BlazeRunConfigurationSyncListener extends SyncListener.Adapter {

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {
    updateExistingRunConfigurations(project);
    if (syncMode == SyncMode.STARTUP) {
      return;
    }

    Set<File> xmlFiles =
        getImportedRunConfigurations(projectViewSet, blazeProjectData.workspacePathResolver);
    Transactions.submitTransactionAndWait(
        () -> {
          // First, import from specified XML files. Then auto-generate from targets.
          xmlFiles.forEach(
              (file) -> RunConfigurationSerializer.loadFromXmlIgnoreExisting(project, file));

          Set<Label> labelsWithConfigs = labelsWithConfigs(project);
          Set<TargetExpression> targetExpressions =
              Sets.newLinkedHashSet(projectViewSet.listItems(TargetSection.KEY));
          // We only auto-generate configurations for rules listed in the project view.
          for (TargetExpression target : targetExpressions) {
            if (!(target instanceof Label) || labelsWithConfigs.contains(target)) {
              continue;
            }
            Label label = (Label) target;
            labelsWithConfigs.add(label);
            maybeAddRunConfiguration(project, blazeProjectData, label);
          }
        });
  }

  /**
   * On each sync, re-calculate target kind for all existing run configurations, in case the target
   * map has changed since the last sync.
   */
  private static void updateExistingRunConfigurations(Project project) {
    RunManager runManager = RunManager.getInstance(project);
    for (RunConfiguration runConfig :
        runManager.getConfigurationsList(BlazeCommandRunConfigurationType.getInstance())) {
      if (runConfig instanceof BlazeCommandRunConfiguration) {
        ((BlazeCommandRunConfiguration) runConfig).updateTargetKindAsync(null);
      }
    }
  }

  private static Set<File> getImportedRunConfigurations(
      ProjectViewSet projectViewSet, WorkspacePathResolver pathResolver) {
    return projectViewSet
        .listItems(RunConfigurationsSection.KEY)
        .stream()
        .map(pathResolver::resolveToFile)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Collects a set of all the Blaze labels that have an associated run configuration. */
  private static Set<Label> labelsWithConfigs(Project project) {
    List<RunConfiguration> configurations =
        RunManager.getInstance(project).getAllConfigurationsList();
    Set<Label> labelsWithConfigs = Sets.newHashSet();
    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof BlazeRunConfiguration) {
        BlazeRunConfiguration blazeRunConfiguration = (BlazeRunConfiguration) configuration;
        TargetExpression target = blazeRunConfiguration.getTarget();
        if (target instanceof Label) {
          labelsWithConfigs.add((Label) target);
        }
      }
    }
    return labelsWithConfigs;
  }

  /**
   * Adds a run configuration for an android_binary target if there is not already a configuration
   * for that target.
   */
  private static void maybeAddRunConfiguration(
      Project project, BlazeProjectData blazeProjectData, Label label) {
    RunManager runManager = RunManager.getInstance(project);

    for (BlazeRunConfigurationFactory configurationFactory :
        BlazeRunConfigurationFactory.EP_NAME.getExtensions()) {
      if (configurationFactory.handlesTarget(project, blazeProjectData, label)) {
        final RunnerAndConfigurationSettings settings =
            configurationFactory.createForTarget(project, runManager, label);
        runManager.addConfiguration(settings, /* isShared */ false);
        if (runManager.getSelectedConfiguration() == null) {
          runManager.setSelectedConfiguration(settings);
        }
        break;
      }
    }
  }
}
