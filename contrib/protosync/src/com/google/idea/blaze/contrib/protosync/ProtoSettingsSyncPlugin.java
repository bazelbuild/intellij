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
package com.google.idea.blaze.contrib.protosync;

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.util.Collection;

public class ProtoSettingsSyncPlugin implements BlazeSyncPlugin {
  private static final String PLUGIN_ID = "io.protostuff.protostuff-jetbrains-plugin";

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (ProtoSettingsSyncSections.hasProtoEntries(projectViewSet)
        && !PluginUtils.isPluginInstalled(PLUGIN_ID)) {
      IssueOutput.issue(
              IssueOutput.Category.INFORMATION,
              ProtoSettingsSyncSections.PROTO_IMPORT_ROOTS.getSectionKey().getName()
                  + " found in project view set. Click here to install/enable the plugin")
          .navigatable(PluginUtils.installOrEnablePluginNavigable(PLUGIN_ID))
          .submit(context);
    }
    return true;
  }

  @Override
  public void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      @Nullable WorkingSet workingSet,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      BlazeSyncParams.SyncMode syncMode) {
    if (PluginUtils.isPluginInstalled(PLUGIN_ID)) {
      ProtoSettingsSyncUpdater.syncSettings(project, projectViewSet, context);
    }
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ProtoSettingsSyncSections.PARSERS;
  }
}
