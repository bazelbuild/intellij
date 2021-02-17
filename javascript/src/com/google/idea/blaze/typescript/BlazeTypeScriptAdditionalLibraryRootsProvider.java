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
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TsIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalLibraryProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The tsconfig library only contains .d.ts files under tsconfig.runfiles. We need this to provide
 * the source .ts files so we can resolve to them.
 */
public final class BlazeTypeScriptAdditionalLibraryRootsProvider
    extends BlazeExternalLibraryProvider {
  static final BoolExperiment useTypeScriptAdditionalLibraryRootsProvider =
      new BoolExperiment("use.typescript.additional.library.roots.provider4", true);
  static final BoolExperiment moveTsconfigFilesToAdditionalLibrary =
      new BoolExperiment("move.tsconfig.files.to.additional.library", true);

  @Override
  protected String getLibraryName() {
    return "TypeScript Libraries";
  }

  @Override
  protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
    if (!useTypeScriptAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    return importRoots != null
        ? getLibraryFiles(project, projectData, importRoots)
        : ImmutableList.of();
  }

  static ImmutableList<File> getLibraryFiles(
      Project project, BlazeProjectData projectData, ImportRoots importRoots) {
    if (!projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return ImmutableList.of();
    }
    return Stream.concat(
            filesFromTargetMap(project, projectData, importRoots),
            filesFromTsConfig(project, importRoots))
        .collect(toImmutableList());
  }

  private static Stream<File> filesFromTargetMap(
      Project project, BlazeProjectData projectData, ImportRoots importRoots) {
    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    return projectData.getTargetMap().targets().stream()
        .filter(t -> t.getTsIdeInfo() != null)
        .map(TargetIdeInfo::getTsIdeInfo)
        .map(TsIdeInfo::getSources)
        .flatMap(Collection::stream)
        .filter(BlazeTypeScriptAdditionalLibraryRootsProvider::isTypeScriptArtifact)
        .filter(location -> isExternalArtifact(importRoots, location))
        .distinct()
        .map(a -> OutputArtifactResolver.resolve(project, decoder, a))
        .filter(Objects::nonNull);
  }

  private static Stream<File> filesFromTsConfig(Project project, ImportRoots importRoots) {
    if (!moveTsconfigFilesToAdditionalLibrary.getValue()) {
      return Stream.of();
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    TypeScriptConfigService service = TypeScriptConfigService.Provider.get(project);
    if (!(service instanceof DelegatingTypeScriptConfigService)) {
      return Stream.of();
    }
    List<TypeScriptConfig> configs =
        ((DelegatingTypeScriptConfigService) service).getTypeScriptConfigs();
    return configs.stream()
        .map(TypeScriptConfig::getFileList)
        .flatMap(Collection::stream)
        .map(VfsUtil::virtualToIoFile)
        .filter(file -> isExternalFile(workspaceRoot, importRoots, file));
  }

  private static boolean isTypeScriptArtifact(ArtifactLocation location) {
    String extension = Files.getFileExtension(location.getRelativePath());
    return TypeScriptPrefetchFileSource.getTypeScriptExtensions().contains(extension);
  }

  private static boolean isExternalArtifact(ImportRoots importRoots, ArtifactLocation location) {
    if (!location.isSource()) {
      return true;
    }
    WorkspacePath workspacePath = WorkspacePath.createIfValid(location.getRelativePath());
    return workspacePath == null || !importRoots.containsWorkspacePath(workspacePath);
  }

  private static boolean isExternalFile(
      WorkspaceRoot workspaceRoot, ImportRoots importRoots, File file) {
    return !workspaceRoot.isInWorkspace(file)
        || !importRoots.containsWorkspacePath(workspaceRoot.workspacePathFor(file));
  }
}
