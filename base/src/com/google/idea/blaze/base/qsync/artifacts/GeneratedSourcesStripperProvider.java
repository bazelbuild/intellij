/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.artifacts;

import com.google.idea.blaze.qsync.artifacts.FileTransform;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/**
 * Provider for the generated sources stripper, since the class depends on java-specific classes.
 */
public interface GeneratedSourcesStripperProvider {

  ExtensionPointName<GeneratedSourcesStripperProvider> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.base.qsync.GeneratedSourcesStripperProvider");

  static FileTransform get(Project project) {
    return EP_NAME.getExtensionList().stream()
        .map(it -> it.createTransformer(project))
        .findFirst()
        .orElse(FileTransform.COPY);
  }

  FileTransform createTransformer(Project project);
}
