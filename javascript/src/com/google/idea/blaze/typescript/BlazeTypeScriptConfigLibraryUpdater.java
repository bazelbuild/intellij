/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.typescript;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.sdkcompat.javascript.TypeScriptConfigLibraryUpdaterAdapter;
import com.intellij.lang.javascript.library.JSLibraryMappings;
import com.intellij.lang.typescript.TypeScriptSettings;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigLibraryUpdater;
import com.intellij.openapi.project.Project;

/**
 * Dummy {@link TypeScriptConfigLibraryUpdater} that does nothing, to prevent tsconfig$paths from
 * being created.
 */
class BlazeTypeScriptConfigLibraryUpdater extends TypeScriptConfigLibraryUpdaterAdapter {
  private final Project project;
  private final boolean isBlaze;
  private boolean initialized;

  public BlazeTypeScriptConfigLibraryUpdater(Project project) {
    super(project);
    this.project = project;
    this.isBlaze = Blaze.isBlazeProject(project);
    this.initialized = false;
  }

  @Override
  public void queueToUpdate() {
    if (isBlaze
        && BlazeTypeScriptAdditionalLibraryRootsProvider.useTypeScriptAdditionalLibraryRootsProvider
            .getValue()) {
      if (initialized) {
        return;
      }
      initialized = true;
      JSLibraryMappings mappings = JSLibraryMappings.getInstance(project);
      if (BlazeTypeScriptAdditionalLibraryRootsProvider.moveTsconfigFilesToAdditionalLibrary
          .getValue()) {
        if (mappings.isAssociatedWithProject(
            TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY)) {
          mappings.disassociateWithProject(TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY);
        }
      } else {
        if (!mappings.isAssociatedWithProject(
            TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY)) {
          mappings.associate(null, TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY, true);
        }

        TypeScriptSettings settings = TypeScriptSettings.getSettings(project);
        if (settings != null) {
          settings.setAutoIncludeConfigPaths(true);
          super.queueToUpdate();
          settings.setAutoIncludeConfigPaths(false);
        }
      }
    } else {
      super.queueToUpdate();
    }
  }
}
