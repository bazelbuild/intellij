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
import java.util.Iterator;
import java.util.List;

/** Helper class to add plugin dependencies to the project */
public class PluginDependencyHelper {

  public static void addDependencyOnSyncPlugin(Project blazeProject) {
    BlazePluginId idService = BlazePluginId.getInstance();
    if (idService != null) {
      addDependency(
          blazeProject, new DependencyOnPlugin(idService.getPluginId(), null, null, null));
    }
  }

  /**
   * Removes a project depedency on a given plugin, if one exists. Doesn't trigger any update
   * checking. This is to handle migration of the IntelliJ-with-Bazel plugin to a different plugin
   * ID. This is introduced in v1.9, remove in v2.2+
   */
  @Deprecated
  public static void removeDependencyOnOldPlugin(Project project, String pluginId) {
    ExternalDependenciesManager manager = ExternalDependenciesManager.getInstance(project);
    List<ProjectExternalDependency> deps = Lists.newArrayList(manager.getAllDependencies());
    Iterator<ProjectExternalDependency> iter = deps.iterator();
    while (iter.hasNext()) {
      ProjectExternalDependency dep = iter.next();
      if (!(dep instanceof DependencyOnPlugin)) {
        continue;
      }
      DependencyOnPlugin pluginDep = (DependencyOnPlugin) dep;
      if (pluginDep.getPluginId().equals(pluginId)) {
        iter.remove();
      }
    }
    manager.setAllDependencies(deps);
  }

  /**
   * Adds dependency, or replaces existing dependency of same type. Doesn't trigger any update
   * checking
   */
  private static void addDependency(Project project, DependencyOnPlugin newDep) {

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
