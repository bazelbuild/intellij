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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * Indirection between ide_build_info and aspect style IDE info.
 */
public abstract class BlazeIdeInterface {

  public static BlazeIdeInterface getInstance() {
    return ServiceManager.getService(BlazeIdeInterface.class);
  }

  public static class IdeResult {
    public final ImmutableMap<Label, RuleIdeInfo> ruleMap;
    @Deprecated
    @Nullable
    public final File androidPlatformDirectory;
    public IdeResult(
      ImmutableMap<Label, RuleIdeInfo> ruleMap,
      @Nullable File androidPlatformDirectory) {

      this.ruleMap = ruleMap;
      this.androidPlatformDirectory = androidPlatformDirectory;
    }
  }

  @Nullable
  public abstract IdeResult updateBlazeIdeState(
    Project project,
    BlazeContext context,
    WorkspaceRoot workspaceRoot,
    ProjectViewSet projectViewSet,
    List<TargetExpression> targets,
    WorkspaceLanguageSettings workspaceLanguageSettings,
    ArtifactLocationDecoder artifactLocationDecoder,
    SyncState.Builder syncStateBuilder,
    @Nullable SyncState previousSyncState,
    boolean requiresAndroidSdk);

  public abstract void resolveIdeArtifacts(
    Project project,
    BlazeContext context,
    WorkspaceRoot workspaceRoot,
    ProjectViewSet projectViewSet,
    List<TargetExpression> targets);
}
