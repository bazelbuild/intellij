/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync;

import static java.util.Arrays.stream;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import java.util.Objects;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;

/**
 * The configurator specific for adding kotlin Library to old project model (2020.3 and 2021.1).
 * This is a temporary workaround for b/198439707. TODO(b/198439707): Delete when old project model
 * is not in use anymore.
 */
public class KotlinLibraryConfiguratorForOldProjectModel extends KotlinJavaModuleConfigurator {
  static final KotlinLibraryConfiguratorForOldProjectModel INSTANCE =
      new KotlinLibraryConfiguratorForOldProjectModel();

  /**
   * Add kotlin library to {@link ModifiableRootModel} when it does not have one. This is similar
   * with {@link KotlinWithLibraryConfigurator#addLibraryToModuleIfNeeded}. But {@link
   * KotlinWithLibraryConfigurator} does not provide a method to update {@link ModifiableRootModel}.
   */
  void configureModel(Project project, ModifiableRootModel modifiableRootModel, Module module) {
    if (stream(modifiableRootModel.getModuleLibraryTable().getLibraries())
        .anyMatch(
            // mimics KotlinWithLibraryConfigurator#isKotlinLibrary
            library ->
                Objects.equals(library.getName(), getLibraryName())
                    || getLibraryMatcher().invoke(library, project))) {
      return;
    }
    modifiableRootModel.addLibraryEntry(
        getKotlinLibrary(
            project,
            module,
            new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin")));
  }

  /**
   * Get a kotlin library that can be added to module. This is part of {@link
   * KotlinJavaModuleConfigurator#configureModule}.
   */
  private Library getKotlinLibrary(
      Project project, Module module, NotificationMessageCollector collector) {
    Library library = findAndFixBrokenKotlinLibrary(module, collector);
    if (library != null) {
      return library;
    }
    library = getKotlinLibrary(module);
    if (library != null) {
      return library;
    }
    library = getKotlinLibrary(project);
    if (library != null) {
      return library;
    }
    return createNewLibrary(project, collector);
  }
}
