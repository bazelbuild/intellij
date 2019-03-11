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
import com.intellij.lang.javascript.library.JSLibraryMappings;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigLibraryUpdater;
import com.intellij.openapi.project.Project;

/**
 * Dummy {@link TypeScriptConfigLibraryUpdater} that does nothing, to prevent tsconfig$paths from
 * being created.
 */
class BlazeTypeScriptConfigLibraryUpdater extends TypeScriptConfigLibraryUpdater {
  private final Project project;
  private final boolean isBlaze;

  public BlazeTypeScriptConfigLibraryUpdater(Project project) {
    super(project);
    this.project = project;
    this.isBlaze = Blaze.isBlazeProject(project);
  }

  @Override
  public void queueToUpdate() {
    if (isBlaze
        && BlazeTypeScriptAdditionalLibraryRootsProvider.useTypeScriptAdditionalLibraryRootsProvider
            .getValue()) {
      JSLibraryMappings mappings = JSLibraryMappings.getInstance(project);
      if (mappings.isAssociatedWithProject(TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY)) {
        mappings.disassociateWithProject(TypeScriptConfigLibraryUpdater.TSCONFIG_PATHS_LIBRARY);
      }
    } else {
      super.queueToUpdate();
    }
  }
}
