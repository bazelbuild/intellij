/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.jetbrains.cidr.cpp.CPPModuleType;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Suppress {@link CMakeWorkspace#projectOpened} for non-CMake projects. Remove this if the <a
 * href="https://youtrack.jetbrains.com/issue/CPP-9632">upstream bug</a> is fixed.
 */
public class CMakeWorkspaceOverride extends CMakeWorkspace {

  private final boolean isBlazeProject;

  public CMakeWorkspaceOverride(Project project) {
    super(project);
    isBlazeProject = Blaze.isBlazeProject(project);
  }

  @Override
  public void projectOpened() {
    if (!isBlazeProject) {
      super.projectOpened();
      return;
    }
    removeClasspathStorageFromModules(myProject);
  }

  /**
   * A hacky way of removing the classpath ID. {@link ClasspathStorage} doesn't have a method for
   * removing the existing storage type, but #setStorageType will silently do this if it's passed an
   * unrecognized type.
   */
  private static void removeClasspathStorageFromModules(Project project) {
    String dummyClasspathId = "classpath.id.which.does.not.exist";
    for (Module cppModule : getCppModules(project)) {
      ClasspathStorage.setStorageType(ModuleRootManager.getInstance(cppModule), dummyClasspathId);
    }
  }

  private static List<Module> getCppModules(Project project) {
    ModuleType<?> type = CPPModuleType.getInstance();
    return Arrays.stream(ModuleManager.getInstance(project).getModules())
        .filter(module -> type.equals(ModuleType.get(module)))
        .collect(Collectors.toList());
  }
}
