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
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import javax.annotation.Nullable;

/**
 * #api181 2018.2 and later uses tsconfig$paths via {@link AdditionalLibraryRootsProvider} instead
 * of a tsconfig$roots library.
 */
class BlazeTypeScriptLibrarySourceProvider {
  @Nullable
  static LibrarySource getLibrarySource() {
    return null;
  }

  static void addTsConfigLibrary(Project project, ModifiableRootModel workspaceModifiableModel) {}
}
