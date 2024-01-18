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
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/** Updates and exposes a map of import paths to files. */
public class BlazeGoPackageFactory implements GoPackageFactory {
  @Nullable
  @Override
  public GoPackage createPackage(GoFile goFile) {
    if (Blaze.getProjectType(goFile.getProject()) == BlazeImportSettings.ProjectType.UNKNOWN) {
      return null;
    }
    VirtualFile virtualFile = goFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    Project project = goFile.getProject();
    ConcurrentMap<File, String> fileToImportPathMap = getFileToImportPathMap(project);
    if (fileToImportPathMap == null) {
      return null;
    }
    String importPath = fileToImportPathMap.get(VfsUtil.virtualToIoFile(virtualFile));
    return importPath != null ? BlazeGoImportResolver.doResolve(importPath, project) : null;
  }

  @Nullable
  public static ConcurrentMap<File, String> getFileToImportPathMap(Project project) {
    return SyncCache.getInstance(project)
        .get(BlazeGoPackageFactory.class, BlazeGoPackageFactory::buildFileToImportPathMap);
  }

  private static ConcurrentMap<File, String> buildFileToImportPathMap(
      Project project, BlazeProjectData projectData) {
    TargetMap targetMap = projectData.getTargetMap();
    ConcurrentMap<File, String> map = new ConcurrentHashMap<>();
    ImmutableMultimap<Label, File> targetToFile =
        BlazeGoPackage.getTargetToFileMap(project, projectData);
    for (TargetIdeInfo target : targetMap.targets()) {
      if (target.getGoIdeInfo() == null) {
        continue;
      }
      String importPath =
          target.getGoIdeInfo().getLibraryLabels().stream()
              .map(TargetKey::forPlainTarget)
              .map(targetMap::get)
              .filter(Objects::nonNull)
              .map(TargetIdeInfo::getGoIdeInfo)
              .filter(Objects::nonNull)
              .map(GoIdeInfo::getImportPath)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(target.getGoIdeInfo().getImportPath());
      if (importPath == null) {
        continue;
      }
      for (File file : targetToFile.get(target.getKey().getLabel())) {
        map.putIfAbsent(file, importPath);
      }
    }
    return map;
  }

  @Nullable
  @Override
  public GoPackage createPackage(String packageName, PsiDirectory... directories) {
    return null;
  }
}
