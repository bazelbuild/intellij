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
package com.google.idea.blaze.ijwb.android;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

/**
 * Rudimentary support for android in IntelliJ.
 */
public class BlazeAndroidLiteSyncPlugin extends BlazeSyncPlugin.Adapter {

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    switch (workspaceType) {
      case ANDROID:
      case JAVA:
        return ImmutableSet.of(LanguageClass.ANDROID);
      default:
        return ImmutableSet.of();
    }
  }

  @Override
  public void updateSyncState(Project project,
                              BlazeContext context,
                              WorkspaceRoot workspaceRoot,
                              ProjectViewSet projectViewSet,
                              WorkspaceLanguageSettings workspaceLanguageSettings,
                              BlazeRoots blazeRoots,
                              @Nullable WorkingSet workingSet,
                              WorkspacePathResolver workspacePathResolver,
                              ImmutableMap<Label, RuleIdeInfo> ruleMap,
                              @Deprecated @Nullable File androidPlatformDirectory,
                              SyncState.Builder syncStateBuilder,
                              @Nullable SyncState previousSyncState) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.ANDROID)) {
      return;
    }

    BlazeAndroidLiteWorkspaceImporter workspaceImporter = new BlazeAndroidLiteWorkspaceImporter(
      project,
      workspaceRoot,
      context,
      projectViewSet,
      ruleMap
    );
    BlazeAndroidLiteImportResult importResult = Scope.push(context, childContext -> {
      childContext.push(new TimingScope("AndroidLiteWorkspaceImporter"));
      return workspaceImporter.importWorkspace();
    });
    BlazeAndroidLiteSyncData syncData = new BlazeAndroidLiteSyncData(importResult);
    syncStateBuilder.put(BlazeAndroidLiteSyncData.class, syncData);
  }
}
