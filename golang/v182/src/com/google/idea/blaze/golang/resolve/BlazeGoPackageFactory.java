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
package com.google.idea.blaze.golang.resolve;

import com.goide.project.GoPackageFactory;
import com.goide.psi.GoFile;
import com.goide.psi.impl.GoPackage;
import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.golang.sync.BlazeGoLibrary;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

class BlazeGoPackageFactory implements GoPackageFactory {
  @Nullable
  @Override
  public GoPackage createPackage(GoFile goFile) {
    VirtualFile virtualFile = goFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    Project project = goFile.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    Map<File, String> fileToImportPathMap =
        Preconditions.checkNotNull(getFileToImportPathMap(project));
    File file = VfsUtil.virtualToIoFile(virtualFile);
    if (fileToImportPathMap.containsKey(file)) {
      String importPath = fileToImportPathMap.get(file);
      return importPath != null ? BlazeGoImportResolver.doResolve(importPath, project) : null;
    }
    GoPackage goPackage = doCreatePackage(project, projectData, file);
    fileToImportPathMap.put(file, goPackage != null ? goPackage.getImportPath(false) : null);
    return goPackage;
  }

  @Nullable
  private static GoPackage doCreatePackage(
      Project project, BlazeProjectData projectData, File file) {
    // Check if file is under blaze_go_library.
    // We can easily find the import path in this case.
    File goLibrary = BlazeGoLibrary.getLibraryRoot(project);
    if (goLibrary != null && FileUtil.isAncestor(goLibrary, file, true)) {
      String importPath = file.getParentFile().getName().replace('-', '/');
      return BlazeGoImportResolver.doResolve(importPath, project);
    }
    // Otherwise, check targets.
    return SourceToTargetFinder.findTargetsForSourceFile(project, file, Optional.empty())
        .stream()
        .map(t -> t.label)
        .map(TargetKey::forPlainTarget)
        .map(projectData.targetMap::get)
        .filter(Objects::nonNull)
        .filter(t -> t.goIdeInfo != null)
        .map(t -> t.goIdeInfo.importPath)
        .map(importPath -> BlazeGoImportResolver.doResolve(importPath, project))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  private static Map<File, String> getFileToImportPathMap(Project project) {
    return SyncCache.getInstance(project)
        .get(BlazeGoPackageFactory.class, (p, pd) -> new HashMap<>());
  }

  @Nullable
  @Override
  public GoPackage createPackage(String packageName, PsiDirectory... directories) {
    return null;
  }

  @Nullable
  @Override
  public GoPackage createPackage(
      String packageName, boolean testsOnly, PsiDirectory... directories) {
    return null;
  }
}
