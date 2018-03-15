/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ijwb.dart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Declare that dart files should be prefetched. */
public class DartPrefetchFileSource implements PrefetchFileSource {

  private static final BoolExperiment prefetchAllDartSources =
      new BoolExperiment("prefetch.all.dart.sources", true);

  private static final ImmutableSet<String> FILE_EXTENSIONS = ImmutableSet.of("dart");

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.DART)
        || !prefetchAllDartSources.getValue()) {
      return;
    }
    // Prefetch all non-project dart source files found during sync
    Predicate<ArtifactLocation> shouldPrefetch =
        location -> {
          if (!location.isSource) {
            return false;
          }
          WorkspacePath path = WorkspacePath.createIfValid(location.relativePath);
          if (path == null || importRoots.containsWorkspacePath(path)) {
            return false;
          }
          String extension = FileUtil.getExtension(path.relativePath());
          return FILE_EXTENSIONS.contains(extension);
        };
    List<File> sourceFiles =
        blazeProjectData
            .targetMap
            .targets()
            .stream()
            .map(DartPrefetchFileSource::getDartSources)
            .flatMap(Collection::stream)
            .filter(shouldPrefetch)
            .map(blazeProjectData.artifactLocationDecoder::decode)
            .collect(Collectors.toList());
    files.addAll(sourceFiles);
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return FILE_EXTENSIONS;
  }

  private static Collection<ArtifactLocation> getDartSources(TargetIdeInfo target) {
    return target.dartIdeInfo != null ? target.dartIdeInfo.sources : ImmutableList.of();
  }
}
