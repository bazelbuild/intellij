/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Implementation of {@link SourceToTargetMap} for query sync. */
public class QuerySyncSourceToTargetMap implements SourceToTargetMap {

  private final Logger logger = Logger.getInstance(getClass());

  private final BlazeProject blazeProject;
  private final Path workspaceRoot;

  public QuerySyncSourceToTargetMap(BlazeProject blazeProject, Path workspaceRoot) {
    this.blazeProject = blazeProject;
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  public ImmutableList<Label> getTargetsToBuildForSourceFile(File file) {
    Path rel = workspaceRoot.relativize(file.toPath());

    BlazeProjectSnapshot snapshot = blazeProject.getCurrent().orElse(null);
    if (snapshot == null) {
      logger.warn("getTargetsToBuildForSourceFile call before sync complete");
      return ImmutableList.of();
    }

    Set<Label> buildTargets = new HashSet<>();
    // TODO(mathewi) this returns a single owner of the source file, whereas the API we're
    //  implementing  expects all such targets.
    com.google.idea.blaze.common.Label targetOwner = snapshot.getTargetOwner(rel);
    if (targetOwner != null) {
      buildTargets.add(Label.create(targetOwner.toString()));
    } else {
      logger.warn(String.format("No target owner found for file %s", rel));
    }
    return ImmutableList.copyOf(buildTargets);
  }

  @Override
  public ImmutableCollection<TargetKey> getRulesForSourceFile(File file) {
    throw new NotSupportedWithQuerySyncException("getRulesForSourceFile");
  }
}
