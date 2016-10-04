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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.confighandler.BlazeUnknownRunConfigurationHandler;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;

/**
 * Added in 1.9 to facilitate updating existing configurations to include the new handler-id and
 * kind attributes. To be removed in 2.1.
 */
public class BlazeCommandRunConfigurationUpdater extends SyncListener.Adapter {
  @Override
  public void onSyncComplete(
      Project project,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncResult syncResult) {
    final RunManager runManager = RunManager.getInstance(project);
    for (RunConfiguration configuration : runManager.getAllConfigurationsList()) {
      if (configuration instanceof BlazeCommandRunConfiguration) {
        BlazeCommandRunConfiguration blazeConfig = (BlazeCommandRunConfiguration) configuration;
        // Only update configurations with unknown handlers, as this will
        // reset any changes made to the handler data since the project loaded.
        if (blazeConfig.getHandler() instanceof BlazeUnknownRunConfigurationHandler
            // Also skip unresolved Label targets; these cannot safely be defaulted
            // to the generic handler and should continue to display an error instead.
            // If the Blaze cache is invalidated, all Labels can be unresolved;
            // blindly updating them would result in loss of handler settings.
            && !(blazeConfig.getTarget() instanceof Label
                && blazeConfig.getRuleForTarget() == null)) {
          blazeConfig.setTarget(blazeConfig.getTarget());
          blazeConfig.loadExternalElementBackup();
        }
      }
    }
  }
}
