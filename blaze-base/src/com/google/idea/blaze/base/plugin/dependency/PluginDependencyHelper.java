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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.plugin.BlazePluginId;
import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PluginDependencyHelper {

  public static void addDependencyOnSyncPlugin(@NotNull Project blazeProject) {
    BlazePluginId idService = BlazePluginId.getInstance();
    if (idService != null) {
      addDependency(
        blazeProject,
        new DependencyOnPlugin(idService.getPluginId(), null, null, null)
      );
    }
  }

  /**
   * Adds dependency, or replaces existing dependency of same type.
   * Doesn't trigger any update checking
   */
  private static void addDependency(
    @NotNull Project project,
    @NotNull DependencyOnPlugin newDep) {

    ExternalDependenciesManager manager = ExternalDependenciesManager.getInstance(project);
    List<ProjectExternalDependency> deps = Lists.newArrayList(manager.getAllDependencies());
    boolean added = false;
    for (int i = 0; i < deps.size(); i++) {
      ProjectExternalDependency dep = deps.get(i);
      if (!(dep instanceof DependencyOnPlugin)) {
        continue;
      }
      DependencyOnPlugin pluginDep = (DependencyOnPlugin) dep;
      if (pluginDep.getPluginId().equals(newDep.getPluginId())) {
        added = true;
        deps.set(i, newDep);
      }
    }
    if (!added) {
      deps.add(newDep);
    }
    manager.setAllDependencies(deps);
  }
}
