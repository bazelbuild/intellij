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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

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
   * @return an SDK appropriate for the project, or null
   */
  Sdk suggestSdk(Project project);
}
