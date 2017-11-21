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
package com.google.idea.blaze.ijwb.dart;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import javax.annotation.Nullable;

/** Handles changes to the Dart plugin libraries between our supported versions. */
final class DartSdkUtils {

  static final String DART_SDK_LIBRARY_NAME = "Dart SDK";

  @Nullable
  static Library findDartLibrary(Project project) {
    return ProjectLibraryTable.getInstance(project).getLibraryByName(DART_SDK_LIBRARY_NAME);
  }
}
