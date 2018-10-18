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
package com.google.idea.blaze.ijwb.typescript;

import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Prevents garbage collection of "tsconfig$roots" */
class BlazeTypeScriptLibrarySourceProvider {
  private static final String TSCONFIG_ROOTS = "tsconfig$roots";

  static LibrarySource getLibrarySource() {
    return new LibrarySource.Adapter() {
      @Nullable
      @Override
      public Predicate<Library> getGcRetentionFilter() {
        return library -> {
          String libraryName = library.getName();
          return libraryName != null && libraryName.equals(TSCONFIG_ROOTS);
        };
      }
    };
  }

  static void addTsConfigLibrary(Project project, ModifiableRootModel workspaceModifiableModel) {
    Library tsConfigLibrary =
        ProjectLibraryTable.getInstance(project).getLibraryByName(TSCONFIG_ROOTS);
    if (tsConfigLibrary != null) {
      if (workspaceModifiableModel.findLibraryOrderEntry(tsConfigLibrary) == null) {
        workspaceModifiableModel.addLibraryEntry(tsConfigLibrary);
      }
    }
  }
}
