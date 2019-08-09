/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

/** Given a source file, finds the targets building it from the blaze sync data. */
public class BlazeSyncSourceToDependencyTargetProvider implements SourceToDependencyTargetProvider {

  @Override
  public Future<List<TargetInfo>> getTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath) {
    Multimap<String, TargetInfo> sourceToDependencyTargetMap =
        getSourceToDependencyTargetMap(project);
    Collection<TargetInfo> targetInfos = sourceToDependencyTargetMap.get(workspaceRelativePath);
    return Futures.immediateFuture(ImmutableList.copyOf(targetInfos));
  }

  private static Multimap<String, TargetInfo> getSourceToDependencyTargetMap(Project project) {
    return SyncCache.getInstance(project)
        .get(
            BlazeSyncSourceToDependencyTargetProvider.class,
            BlazeSyncSourceToDependencyTargetProvider::createSourceToDependencyTargetMap);
  }

  @SuppressWarnings("unused")
  private static Multimap<String, TargetInfo> createSourceToDependencyTargetMap(
      Project project, BlazeProjectData blazeProjectData) {
    ImmutableSetMultimap.Builder<String, TargetInfo> sourceToDependencyTargetMap =
        ImmutableSetMultimap.builder();
    for (TargetIdeInfo targetIdeInfo : blazeProjectData.getTargetMap().targets()) {
      ImmutableSet<ArtifactLocation> sources = targetIdeInfo.getSources();
      if (sources.isEmpty()) {
        continue;
      }
      for (ArtifactLocation source : sources) {
        sourceToDependencyTargetMap.put(
            source.getRelativePath(),
            TargetInfo.builder(
                    targetIdeInfo.getKey().getLabel(), targetIdeInfo.getKind().getKindString())
                .build());
      }
    }
    return sourceToDependencyTargetMap.build();
  }
}
