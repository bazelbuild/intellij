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
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import java.util.Set;

/** Creates run configurations for project view targets, where appropriate. */
public class BlazeRunConfigurationSyncListener extends SyncListener.Adapter {

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncResult syncResult) {

    UIUtil.invokeAndWaitIfNeeded(
        (Runnable)
            () -> {
              Set<Label> labelsWithConfigs = labelsWithConfigs(project);
              Set<TargetExpression> targetExpressions =
                  Sets.newHashSet(projectViewSet.listItems(TargetSection.KEY));
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
    final RunManager runManager = RunManager.getInstance(project);

    for (BlazeRunConfigurationFactory configurationFactory :
        BlazeRunConfigurationFactory.EP_NAME.getExtensions()) {
      if (configurationFactory.handlesTarget(project, blazeProjectData, label)) {
        final RunnerAndConfigurationSettings settings =
            configurationFactory.createForTarget(project, runManager, label);
        runManager.addConfiguration(settings, false /* isShared */);
        if (runManager.getSelectedConfiguration() == null) {
          // TODO(joshgiles): Better strategy for picking initially selected config.
          runManager.setSelectedConfiguration(settings);
        }
        break;
      }
    }
  }
}
