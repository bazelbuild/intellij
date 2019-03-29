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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Declare that go files should be prefetched. */
public class GoPrefetchFileSource
    implements PrefetchFileSource, RemoteOutputsCache.OutputsProvider {

  private static final BoolExperiment prefetchAllGoSources =
      new BoolExperiment("prefetch.all.go.sources", true);

  @Override
  public List<ArtifactLocation> selectOutputsToCache(
      RemoteOutputArtifacts outputs,
      TargetMap targetMap,
      WorkspaceLanguageSettings languageSettings) {
    if (!languageSettings.isLanguageActive(LanguageClass.GO)) {
      return ImmutableList.of();
    }
    return targetMap.targets().stream()
        .filter(t -> t.getGoIdeInfo() != null)
        .map(TargetIdeInfo::getGoIdeInfo)
        .map(GoIdeInfo::getSources)
        .flatMap(Collection::stream)
        .filter(ArtifactLocation::isGenerated)
        .collect(toImmutableList());
  }

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)
        || !prefetchAllGoSources.getValue()) {
      return;
    }
    // Prefetch all non-project go source files found during sync
    Predicate<ArtifactLocation> shouldPrefetch =
        location -> {
          if (location.isGenerated()) {
            return true;
          }
          WorkspacePath path = WorkspacePath.createIfValid(location.getRelativePath());
          return path != null && !importRoots.containsWorkspacePath(path);
        };
    List<File> sourceFiles =
        blazeProjectData.getTargetMap().targets().stream()
            .filter(t -> t.getGoIdeInfo() != null)
            .map(TargetIdeInfo::getGoIdeInfo)
            .map(GoIdeInfo::getSources)
            .flatMap(Collection::stream)
            .filter(shouldPrefetch)
            .map(blazeProjectData.getArtifactLocationDecoder()::decode)
            .collect(Collectors.toList());
    files.addAll(sourceFiles);
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("go");
  }
}
