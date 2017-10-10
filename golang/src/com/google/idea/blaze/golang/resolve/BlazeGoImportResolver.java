/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.goide.psi.GoImportSpec;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * Resolves go imports in a blaze workspace, of the form:
 *
 * <p>"[workspace_name]/path/to/blaze/package/[go_library target]"
 *
 * <p>Only the first non-null import candidate is considered, so all blaze-specific import handling
 * is done in this {@link GoImportResolver}, to more easily manage priority.
 */
public class BlazeGoImportResolver implements GoImportResolver {
  @Nullable
  @Override
  public PsiDirectory resolve(GoImportSpec goImportSpec) {
    Project project = goImportSpec.getProject();
    if (!Blaze.isBlazeProject(project) || !BlazeGoSupport.blazeGoSupportEnabled.getValue()) {
      return null;
    }
    ConcurrentMap<String, BlazeVirtualGoPackage> goPackageMap = getPackageMap(project);
    if (goPackageMap == null) {
      return null;
    }
    BlazeVirtualGoPackage goPackage = goPackageMap.get(goImportSpec.getPath());
    if (goPackage == null) {
      return null;
    }
    return PsiManager.getInstance(project).findDirectory(goPackage);
  }

  public static ConcurrentMap<String, BlazeVirtualGoPackage> getPackageMap(Project project) {
    return SyncCache.getInstance(project)
        .get(BlazeGoImportResolver.class, BlazeGoImportResolver::buildPackageMap);
  }

  private static ConcurrentMap<String, BlazeVirtualGoPackage> buildPackageMap(
      Project project, BlazeProjectData projectData) {
    ConcurrentMap<String, BlazeVirtualGoPackage> goPackageMap = Maps.newConcurrentMap();
    ArtifactLocationDecoder decoder = projectData.artifactLocationDecoder;
    for (TargetIdeInfo target : projectData.targetMap.targets()) {
      if (target.goIdeInfo == null) {
        continue;
      }
      String importPath = target.goIdeInfo.importPath;
      // TODO(chaorenl): warn about null import path
      if (importPath == null || goPackageMap.containsKey(importPath)) {
        continue;
      }
      File importPathFile = new File(importPath);
      goPackageMap.put(
          importPath,
          new BlazeVirtualGoPackage(
              importPathFile.getName(),
              BlazeVirtualGoDirectory.getInstance(project, importPathFile.getParent()),
              getGoPackageSources(target, decoder, projectData.blazeInfo)));
    }
    return goPackageMap;
  }

  private static ImmutableList<File> getGoPackageSources(
      TargetIdeInfo target, ArtifactLocationDecoder decoder, BlazeInfo blazeInfo) {
    if (target.kind.equals(Kind.GO_WRAP_CC)) {
      return ImmutableList.of(getWrapCcGoFile(target, blazeInfo));
    }
    return ImmutableList.copyOf(decoder.decodeAll(target.goIdeInfo.sources));
  }

  private static File getWrapCcGoFile(TargetIdeInfo target, BlazeInfo blazeInfo) {
    String blazePackage = target.key.label.blazePackage().relativePath();
    File directory = new File(blazeInfo.getGenfilesDirectory(), blazePackage);
    String filename = blazePackage + '/' + target.key.label.targetName() + ".go";
    filename = filename.replace("_", "__");
    filename = filename.replace('/', '_');
    return new File(directory, filename);
  }
}
