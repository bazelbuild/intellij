/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testmap;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Used to locate tests from source files for things like right-clicks.
 *
 * <p>It's essentially a map from source file -> reachable test rules.
 */
public class ProjectSourceToTargetFinder implements SourceToTargetFinder {

  @Override
  public Future<Collection<TargetInfo>> targetsForSourceFiles(
      Project project, Set<File> sourceFiles, Optional<RuleType> ruleType) {
    FilteredTargetMap filteredTargetMap =
        SyncCache.getInstance(project)
            .get(ProjectSourceToTargetFinder.class, ProjectSourceToTargetFinder::computeTargetMap);
    if (filteredTargetMap == null) {
      return Futures.immediateFuture(ImmutableList.of());
    }
    ImmutableSet<TargetInfo> targets =
        sourceFiles.stream()
            .flatMap(f -> targetsForSourceFile(filteredTargetMap, f, ruleType).stream())
            .collect(toImmutableSet());
    return Futures.immediateFuture(targets);
  }

  private static ImmutableSet<TargetInfo> targetsForSourceFile(
      FilteredTargetMap targetMap, File sourceFile, Optional<RuleType> ruleType) {
    return targetMap.targetsForSourceFile(sourceFile).stream()
        .map(TargetIdeInfo::toTargetInfo)
        .filter(target -> !ruleType.isPresent() || target.getRuleType().equals(ruleType.get()))
        .collect(toImmutableSet());
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return computeTargetMap(
        project, projectData.getArtifactLocationDecoder(), projectData.getTargetMap());
  }

  private static FilteredTargetMap computeTargetMap(
      Project project, ArtifactLocationDecoder decoder, TargetMap targetMap) {
    return new FilteredTargetMap(project, decoder, targetMap, t -> true);
  }
}
