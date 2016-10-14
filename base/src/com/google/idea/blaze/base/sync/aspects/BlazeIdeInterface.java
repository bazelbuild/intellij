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
package com.google.idea.blaze.base.sync.aspects;

import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/** Indirection between ide_build_info and aspect style IDE info. */
public interface BlazeIdeInterface {

  static BlazeIdeInterface getInstance() {
    return ServiceManager.getService(BlazeIdeInterface.class);
  }

  /** The result of a blaze operation */
  enum BuildResult {
    SUCCESS, // Success
    BUILD_ERROR, // Return code 1, a build error
    FATAL_ERROR; // Some other failure

    public static BuildResult fromExitCode(int exitCode) {
      if (exitCode == 0) {
        return SUCCESS;
      } else if (exitCode == 1) {
        return BUILD_ERROR;
      }
      return FATAL_ERROR;
    }
  }

  /** The result of the ide operation */
  class IdeResult {
    @Nullable public final RuleMap ruleMap;
    public final BuildResult buildResult;

    public IdeResult(@Nullable RuleMap ruleMap, BuildResult buildResult) {
      this.ruleMap = ruleMap;
      this.buildResult = buildResult;
    }
  }

  /**
   * Queries blaze to update the rule map for the given targets.
   *
   * @param mergeWithOldState If true, we overlay the given targets to the current rule map.
   * @return A tuple of the latest updated rule map and the result of the operation.
   */
  IdeResult updateRuleMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      List<TargetExpression> targets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      boolean mergeWithOldState);

  /**
   * Attempts to resolve the requested ide artifacts.
   *
   * <p>Amounts to a build of the ide-resolve output group.
   */
  BuildResult resolveIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      List<TargetExpression> targets);
}
