/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.GenericSourceFolderProvider;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.cidr.cpp.CPPModuleType;
import javax.annotation.Nullable;

class BlazeCLionSyncPlugin extends BlazeSyncPlugin.Adapter {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.C);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.C;
  }

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.C) {
      return CPPModuleType.getInstance();
    }
    return null;
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.workspaceLanguageSettings.isWorkspaceType(WorkspaceType.C)) {
      return null;
    }
    return GenericSourceFolderProvider.INSTANCE;
  }

}
