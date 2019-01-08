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
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalSyntheticLibrary;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.golang.resolve.BlazeGoPackage;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

class BlazeGoAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider {
  private static final BoolExperiment useGoAdditionalLibraryRootsProvider =
      new BoolExperiment("use.go.additional.library.roots.provider4", true);

  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    if (!useGoAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    SyntheticLibrary library =
        SyncCache.getInstance(project)
            .get(getClass(), BlazeGoAdditionalLibraryRootsProvider::getLibrary);
    return library != null && !library.getSourceRoots().isEmpty()
        ? ImmutableList.of(library)
        : ImmutableList.of();
  }

  @Nullable
  private static SyntheticLibrary getLibrary(Project project, BlazeProjectData projectData) {
    ImmutableList<VirtualFile> files = getLibraryFiles(project, projectData);
    return files.isEmpty() ? null : new BlazeExternalSyntheticLibrary("Go Libraries", files);
  }

  private static ImmutableList<VirtualFile> getLibraryFiles(
      Project project, BlazeProjectData projectData) {
    if (!projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return ImmutableList.of();
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (workspaceRoot == null || importRoots == null) {
      return ImmutableList.of();
    }
    Predicate<File> isExternal =
        f -> {
          WorkspacePath path = workspaceRoot.workspacePathForSafe(f);
          return path == null || !importRoots.containsWorkspacePath(path);
        };
    return projectData.getTargetMap().targets().stream()
        .filter(t -> t.getGoIdeInfo() != null)
        .flatMap(t -> BlazeGoPackage.getSourceFiles(t, project, projectData).stream())
        .filter(isExternal)
        .distinct()
        .map(VfsUtils::resolveVirtualFile)
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }
}
