/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.module.ModuleDependencies;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.module.Module;
import java.nio.file.Path;

/** Blaze implementation of {@link AndroidModuleSystem}. */
public class BlazeModuleSystem extends BlazeModuleSystemBase {

  BlazeModuleSystem(Module module) {
    super(module);
  }

  @Override
  public ModuleDependencies getModuleDependencies() {
    return new BlazeModuleDependencies(getModule());
  }

  /** Check every supporting extension point if they contain desugaring library config files */
  @Override
  public boolean getDesugarLibraryConfigFilesKnown() {
    return DesugaringLibraryConfigFilesLocator.forBuildSystem(
            Blaze.getBuildSystemName(module.getProject()))
        .stream()
        .anyMatch(provider -> provider.getDesugarLibraryConfigFilesKnown());
  }

  /** Collect desugarig library config files from every supporting extension and return the list */
  @Override
  public ImmutableList<Path> getDesugarLibraryConfigFiles() {
    return DesugaringLibraryConfigFilesLocator.forBuildSystem(
            Blaze.getBuildSystemName(module.getProject()))
        .stream()
        .flatMap(provider -> provider.getDesugarLibraryConfigFiles(project).stream())
        .collect(toImmutableList());
  }
}
