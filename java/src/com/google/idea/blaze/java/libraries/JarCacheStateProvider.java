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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/** Checks if JAR files should be cached in local file system. */
public class JarCacheStateProvider {

  public static JarCacheStateProvider getInstance(Project project) {
    return ServiceManager.getService(project, JarCacheStateProvider.class);
  }

  private final Project project;

  public JarCacheStateProvider(Project project) {
    this.project = project;
  }

  /** Checks if JAR cache is enabled. */
  public boolean isEnabled() {
    return !ApplicationManager.getApplication().isUnitTestMode()
        && (BlazeJavaUserSettings.getInstance().getUseJarCache()
            || Blaze.getBuildSystemProvider(project).syncingRemotely());
  }
}
