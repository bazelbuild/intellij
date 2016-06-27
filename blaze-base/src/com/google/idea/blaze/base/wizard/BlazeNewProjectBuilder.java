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
package com.google.idea.blaze.base.wizard;

import com.google.idea.blaze.base.plugin.dependency.PluginDependencyHelper;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class BlazeNewProjectBuilder {
  private static final Logger LOG = Logger.getInstance(BlazeNewProjectBuilder.class);

  public static List<Module> commit(final Project project, BlazeImportSettings importSettings, ProjectView projectView) {
    String projectDataDirectory = importSettings.getProjectDataDirectory();

    if (!StringUtil.isEmpty(projectDataDirectory)) {
      File projectDataDir = new File(projectDataDirectory);
      if (!projectDataDir.exists()) {
        if (!projectDataDir.mkdirs()) {
          LOG.error("Unable to create the project directory: " + projectDataDirectory);
        }
      }
    }

    BlazeImportSettingsManager.getInstance(project).setImportSettings(importSettings);

    try {
      String projectViewFile = importSettings.getProjectViewFile();
      LOG.assertTrue(projectViewFile != null);
      ProjectViewStorageManager.getInstance().writeProjectView(
          ProjectViewParser.projectViewToString(projectView),
          new File(projectViewFile)
      );
    } catch (IOException e) {
      LOG.error(e);
    }

    PluginDependencyHelper.addDependencyOnSyncPlugin(project);

    // Initial sync of the project happens in BlazeSyncStartupActivity

    return Collections.emptyList();
  }

}
