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
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalLibraryProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.golang.resolve.BlazeGoPackage;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SyntheticLibrary;
import java.io.File;
import java.util.Collection;

/** Provides out-of-project go sources for indexing. */
public final class BlazeGoAdditionalLibraryRootsProvider extends BlazeExternalLibraryProvider {
  private static final BoolExperiment useGoAdditionalLibraryRootsProvider =
      new BoolExperiment("use.go.additional.library.roots.provider4", true);
  private static ImmutableMultimap<String, File> importpathToFilesMap;


  @Override
  protected String getLibraryName() {
    return "Go Libraries";
  }

  @Override
  protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
    if (!useGoAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
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
    // don't use sync cache, because
    // 1. this is used during sync before project data is saved
    // 2. the roots provider is its own cache
    importpathToFilesMap = BlazeGoPackage.getUncachedExternalImportpathToFilesMap(project, projectData, importRoots, workspaceRoot);
    return importpathToFilesMap.values().asList();
  }

  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    return importpathToFilesMap != null
            ? ImmutableList.of(new BlazeGoExternalSyntheticLibrary(getLibraryName(), importpathToFilesMap))
            : ImmutableList.of();
  }
}
