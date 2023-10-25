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
package com.google.idea.blaze.qsync.cc;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectProto;
import java.util.Optional;

/** Updates the project proto with the output from a CC dependencies build. */
@SuppressWarnings("UnusedVariable") // TODO (b/301235884) remove when this class is implemented
public class CcWorkspaceBuilder {

  private final CcDependenciesInfo ccDependenciesInfo;
  private final BuildGraphData graph;
  private final Context<?> context;

  public CcWorkspaceBuilder(
      CcDependenciesInfo ccDepsInfo, BuildGraphData graph, Context<?> context) {
    this.ccDependenciesInfo = ccDepsInfo;
    this.graph = graph;
    this.context = context;
  }

  public ProjectProto.Project updateProjectProtoForCcDeps(ProjectProto.Project projectProto) {
    return createWorkspace()
        .map(w -> projectProto.toBuilder().setCcWorkspace(w).build())
        .orElse(projectProto);
  }

  @VisibleForTesting
  Optional<ProjectProto.CcWorkspace> createWorkspace() {
    if (ccDependenciesInfo.targetInfoMap().isEmpty()) {
      return Optional.empty();
    }
    // TODO(b/301235884) implement this.
    return Optional.empty();
  }
}
