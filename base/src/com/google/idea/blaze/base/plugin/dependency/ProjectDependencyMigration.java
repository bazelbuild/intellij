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
package com.google.idea.blaze.base.plugin.dependency;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;

/**
 * Temporary migration code. Listens for blaze projects opening and closing, and adds required
 * plugin dependencies
 */
public class ProjectDependencyMigration implements ApplicationComponent {

  @Override
  public void initComponent() {
    ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.addProjectManagerListener(
        new ProjectManagerAdapter() {
          @Override
          public void projectOpened(Project project) {
            if (Blaze.isBlazeProject(project)) {
              PluginDependencyHelper.addDependencyOnSyncPlugin(project);
            }
          }
        });
  }
}
