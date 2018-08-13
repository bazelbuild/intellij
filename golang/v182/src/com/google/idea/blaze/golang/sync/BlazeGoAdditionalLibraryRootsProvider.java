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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.golang.resolve.BlazeGoPackage;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import java.io.File;
import java.util.Collection;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.swing.Icon;

class BlazeGoAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider {
  private static BoolExperiment useGoAdditionalLibraryRootsProvider =
      new BoolExperiment("use.go.additional.library.roots.provider", true);

  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    if (!useGoAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    Library library = SyncCache.getInstance(project).get(getClass(), Library::new);
    return library != null && !library.getSourceRoots().isEmpty()
        ? ImmutableList.of(library)
        : ImmutableList.of();
  }

  private static class Library extends SyntheticLibrary implements ItemPresentation {
    private final ImmutableList<VirtualFile> files;

    Library(Project project, BlazeProjectData projectData) {
      if (!projectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.GO)) {
        this.files = ImmutableList.of();
        return;
      }
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
      ImportRoots importRoots = ImportRoots.forProjectSafe(project);
      if (workspaceRoot == null || importRoots == null) {
        this.files = ImmutableList.of();
        return;
      }
      Predicate<File> isExternal =
          f -> {
            WorkspacePath path = workspaceRoot.workspacePathForSafe(f);
            return path == null || !importRoots.containsWorkspacePath(path);
          };
      this.files =
          projectData
              .targetMap
              .targets()
              .stream()
              .filter(t -> t.goIdeInfo != null)
              .flatMap(t -> BlazeGoPackage.getSourceFiles(t, projectData).stream())
              .filter(isExternal)
              .map(VfsUtils::resolveVirtualFile)
              .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Collection<VirtualFile> getSourceRoots() {
      return files;
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String getPresentableText() {
      return "Go Libraries";
    }

    @Nullable
    @Override
    public String getLocationString() {
      return null;
    }

    @Nullable
    @Override
    public Icon getIcon(boolean unused) {
      return BlazeIcons.Blaze;
    }
  }
}
