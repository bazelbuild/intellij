/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;

/**
 * We want to configure only a single module, without a user-facing dialog (the configuration
 * process takes O(seconds) per module, on the EDT, and there can be 100s of modules for Android
 * Studio).
 *
 * <p>The single-module configuration method isn't exposed though, so we need to subclass the
 * configurator.
 *
 * <p>TODO(brendandouglas): remove this hack as soon as there's an appropriate upstream method.
 */
public class KotlinLibraryConfigurator extends KotlinJavaModuleConfigurator {
  public static final KotlinLibraryConfigurator INSTANCE = new KotlinLibraryConfigurator();

  public void configureModule(Project project, Module module) {
    configureModuleWithLibrary(
        module,
        getDefaultPathToJarFile(project),
        null,
        new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin"));
  }
}
