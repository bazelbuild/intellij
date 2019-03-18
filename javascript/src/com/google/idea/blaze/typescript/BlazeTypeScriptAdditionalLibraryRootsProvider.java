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
package com.google.idea.blaze.typescript;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TsIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalSyntheticLibrary;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * The tsconfig library only contains .d.ts files under tsconfig.runfiles. We need this to provide
 * the source .ts files so we can resolve to them.
 */
class BlazeTypeScriptAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider {
  static final BoolExperiment useTypeScriptAdditionalLibraryRootsProvider =
      new BoolExperiment("use.typescript.additional.library.roots.provider4", true);

  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    if (!useTypeScriptAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    SyntheticLibrary library =
        SyncCache.getInstance(project)
            .get(getClass(), BlazeTypeScriptAdditionalLibraryRootsProvider::getLibrary);
    return library != null && !library.getSourceRoots().isEmpty()
        ? ImmutableList.of(library)
        : ImmutableList.of();
  }

  @Nullable
  private static SyntheticLibrary getLibrary(Project project, BlazeProjectData projectData) {
    ImmutableList<File> files = getLibraryFiles(project, projectData);
    ListenableFuture<Collection<File>> futureFiles = getFutureLibraryFiles(project, projectData);
    return files.isEmpty()
        ? null
        : new BlazeExternalSyntheticLibrary(project, "TypeScript Libraries", files, futureFiles);
  }

  private static ImmutableList<File> getLibraryFiles(
      Project project, BlazeProjectData projectData) {
    if (!projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return ImmutableList.of();
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (importRoots == null) {
      return ImmutableList.of();
    }
    Set<String> tsExtensions = TypeScriptPrefetchFileSource.getTypeScriptExtensions();
    Predicate<ArtifactLocation> isTs =
        (location) -> {
          String extension = Files.getFileExtension(location.getRelativePath());
          return tsExtensions.contains(extension);
        };
    Predicate<ArtifactLocation> isExternal =
        (location) -> {
          if (!location.isSource()) {
            return true;
          }
          WorkspacePath workspacePath = WorkspacePath.createIfValid(location.getRelativePath());
          return workspacePath == null || !importRoots.containsWorkspacePath(workspacePath);
        };
    return projectData.getTargetMap().targets().stream()
        .filter(t -> t.getTsIdeInfo() != null)
        .map(TargetIdeInfo::getTsIdeInfo)
        .map(TsIdeInfo::getSources)
        .flatMap(Collection::stream)
        .filter(isTs)
        .filter(isExternal)
        .distinct()
        .map(projectData.getArtifactLocationDecoder()::decode)
        .collect(toImmutableList());
  }

  private static ListenableFuture<Collection<File>> getFutureLibraryFiles(
      Project project, BlazeProjectData projectData) {
    return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE)
        .submit(() -> TypeScriptPrefetchFileSource.getFilesFromTsConfigs(project, projectData));
  }
}
