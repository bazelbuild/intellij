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
package com.google.idea.blaze.javascript;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JsIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalLibraryProvider;
import com.google.idea.blaze.base.sync.libraries.ExternalLibraryManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

class BlazeJavascriptAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider
    implements BlazeExternalLibraryProvider {
  private static final BoolExperiment useJavascriptAdditionalLibraryRootsProvider =
      new BoolExperiment("use.javascript.additional.library.roots.provider4", true);

  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    return ExternalLibraryManager.getInstance(project).getLibrary(getClass());
  }

  @Override
  public String getLibraryName() {
    return "JavaScript Libraries";
  }

  @Override
  public ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
    if (!useJavascriptAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    return importRoots != null
        ? getLibraryFiles(project, projectData, importRoots)
        : ImmutableList.of();
  }

  static ImmutableList<File> getLibraryFiles(
      Project project, BlazeProjectData projectData, ImportRoots importRoots) {
    if (!JavascriptPrefetchFileSource.languageActive(projectData.getWorkspaceLanguageSettings())) {
      return ImmutableList.of();
    }
    Set<String> jsExtensions = JavascriptPrefetchFileSource.getJavascriptExtensions();
    Predicate<ArtifactLocation> isJs =
        (location) -> {
          String extension = Files.getFileExtension(location.getRelativePath());
          return jsExtensions.contains(extension);
        };
    Predicate<ArtifactLocation> isExternal =
        (location) -> {
          if (!location.isSource()) {
            return true;
          }
          WorkspacePath workspacePath = WorkspacePath.createIfValid(location.getRelativePath());
          return workspacePath == null || !importRoots.containsWorkspacePath(workspacePath);
        };
    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    return projectData.getTargetMap().targets().stream()
        .filter(t -> t.getJsIdeInfo() != null)
        .map(TargetIdeInfo::getJsIdeInfo)
        .map(JsIdeInfo::getSources)
        .flatMap(Collection::stream)
        .filter(isJs)
        .filter(isExternal)
        .distinct()
        .map(a -> OutputArtifactResolver.resolve(project, decoder, a))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }
}
