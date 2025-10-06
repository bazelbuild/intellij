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
package com.google.idea.blaze.base.targetmaps;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;

/** Maps source files to their respective targets */
public class AspectSyncSourceToTargetMap implements SourceToTargetMap {
  private final Project project;

  public AspectSyncSourceToTargetMap(Project project) {
    this.project = project;
  }

  @Override
  public void init() {
    getSourceToTargetMap();
  }

  @Override
  public ImmutableList<Label> getTargetsToBuildForSourceFile(File sourceFile) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }
    return getRulesForSourceFile(sourceFile).stream()
        .map(blazeProjectData.targetMap()::get)
        .filter(Objects::nonNull)
        // TODO(tomlu): For non-plain targets we need to rdep our way back to a target to build
        // Without this, you won't be able to invoke "build" on (say) a proto_library
        .filter(TargetIdeInfo::isPlainTarget)
        .map(TargetIdeInfo::getKey)
        .map(TargetKey::label)
        .collect(toImmutableList());
  }

  @Override
  public ImmutableCollection<TargetKey> getRulesForSourceFile(File sourceFile) {
    ImmutableMultimap<File, TargetKey> sourceToTargetMap = getSourceToTargetMap();
    if (sourceToTargetMap == null) {
      return ImmutableList.of();
    }
    return sourceToTargetMap.get(sourceFile);
  }

  @Nullable
  private ImmutableMultimap<File, TargetKey> getSourceToTargetMap() {
    var syncCache = SyncCache.getInstance(project);

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      // we might be called from a non-cancellable read action, and this leads
      // to a freeze as computeSourceToTargetMap is blocking. This is an
      // ijplatform issue as cancellable read actions should be used, but there
      // are still usages of the old noncancellable one. So in case of read action
      // we do not compute the map and just trying to get the computed version and
      // schedule computation if needed
      return syncCache.tryGet(AspectSyncSourceToTargetMap.class, AspectSyncSourceToTargetMap::computeSourceToTargetMap);
    }

    return syncCache.get(
            AspectSyncSourceToTargetMap.class,
            AspectSyncSourceToTargetMap::computeSourceToTargetMap);
  }

  @SuppressWarnings("unused")
  private static ImmutableMultimap<File, TargetKey> computeSourceToTargetMap(
      Project project, BlazeProjectData blazeProjectData) {
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder();
    ImmutableMultimap.Builder<File, TargetKey> sourceToTargetMap = ImmutableMultimap.builder();
    for (TargetIdeInfo target : blazeProjectData.targetMap().targets()) {
      ProgressManager.checkCanceled();

      TargetKey key = target.getKey();
      for (ArtifactLocation sourceArtifact : target.getSources()) {
        File file = decoder.resolveSource(sourceArtifact);
        if (file != null) {
          sourceToTargetMap.put(file, key);
        }
      }
    }
    return sourceToTargetMap.build();
  }
}
