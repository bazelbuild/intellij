/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.sync;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PythonSdkType;
import javax.annotation.Nullable;

/** Extension to allow suggestion of Python SDK to use for a particular project */
public interface PySdkSuggester {
  ExtensionPointName<PySdkSuggester> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.PySdkSuggester");

  /**
   * Suggests an existing Python SDK to use for the given project, otherwise creates an appropriate
   * one, adds it to the registered SDK list and returns it. If it doesn't know a specific SDK to
   * use, return null.
   *
   * @param project the project to suggest the SDK for
   * @param version the python version to suggest an SDK for
   * @return an SDK appropriate for the project, or null
   */
  Sdk suggestSdk(Project project, PythonVersion version);

  /**
   * This is a mechanism allowing the plugin to migrate the suggested SDK. If a project/facet's
   * PythonSDK is considered deprecated, the sync process will treat it as unset.
   *
   * @param sdk an SDK to check for deprecatedness
   * @return a boolean indicated whether sdk is considered deprecated
   */
  boolean isDeprecatedSdk(Sdk sdk);

  /** Utility method for PySdkSuggester to resolve a homepath to a registered SDK. */
  @Nullable
  static Sdk findPythonSdk(String homePath) {
    return PythonSdkType.getAllSdks().stream()
        .filter(sdk -> homePath.equals(sdk.getHomePath()))
        .findAny()
        .orElse(null);
  }
}
