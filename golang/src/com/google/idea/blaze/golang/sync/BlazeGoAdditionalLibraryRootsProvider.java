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
package com.google.idea.blaze.golang.sync;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalLibraryProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.golang.resolve.BlazeGoPackage;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.function.Predicate;

/** Provides out-of-project go sources for indexing. */
public final class BlazeGoAdditionalLibraryRootsProvider extends BlazeExternalLibraryProvider {

  public static final String GO_EXTERNAL_LIBRARY_ROOT_NAME = "Go Libraries";

  @Override
  protected String getLibraryName() {
    return GO_EXTERNAL_LIBRARY_ROOT_NAME;
  }

  @Override
  protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    return importRoots != null
        ? getLibraryFiles(project, projectData, importRoots)
        : ImmutableList.of();
  }

  static ImmutableList<File> getLibraryFiles(
      Project project, BlazeProjectData projectData, ImportRoots importRoots) {
    if (!projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return ImmutableList.of();
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    if (workspaceRoot == null) {
      return ImmutableList.of();
    }
    Predicate<File> isExternal =
        f -> {
          WorkspacePath path = workspaceRoot.workspacePathForSafe(f);
          return path == null || !importRoots.containsWorkspacePath(path);
        };
    // don't use sync cache, because
    // 1. this is used during sync before project data is saved
    // 2. the roots provider is its own cache
    return BlazeGoPackage.getUncachedTargetToFileMap(project, projectData).values().stream()
        .filter(isExternal)
        .filter(f -> f.getName().endsWith(".go"))
        .distinct()
        .collect(toImmutableList());
  }
}
