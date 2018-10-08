/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.sync;

import com.goide.project.GoModuleSettings;
import com.goide.sdk.GoSdk;
import com.goide.sdk.GoSdkService;
import com.goide.sdk.GoSdkUtil;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.common.transactions.Transactions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

/**
 * Runs after sync. Sets up a Go SDK library if Go-lang is active, and there's no existing library
 * set up.
 */
public class BlazeGoSdkUpdater extends SyncListener.Adapter {

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return;
    }
    Module workspaceModule = getWorkspaceModule(project);
    if (workspaceModule == null || GoSdkService.getInstance(project).isGoModule(workspaceModule)) {
      return;
    }
    String sdkPath = getOrSuggestSdkPath(workspaceModule);
    if (sdkPath != null) {
      setSdkPath(project, workspaceModule, sdkPath);
    }
  }

  private static void setSdkPath(Project project, Module workspaceModule, String path) {
    Transactions.submitTransactionAndWait(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    () -> {
                      GoSdkService.getInstance(project).setSdkHomePath(path);
                      GoModuleSettings.getInstance(workspaceModule).setGoSupportEnabled(true);
                    }));
  }

  @Nullable
  private static String getOrSuggestSdkPath(Module module) {
    GoSdk sdk = GoSdkService.getInstance(module.getProject()).getSdk(module);
    if (sdk != GoSdk.NULL) {
      return sdk.getHomePath();
    }
    VirtualFile defaultSdk = GoSdkUtil.suggestSdkDirectory();
    return defaultSdk != null ? defaultSdk.getPath() : null;
  }

  @Nullable
  private static Module getWorkspaceModule(Project project) {
    return ReadAction.compute(
        () ->
            ModuleManager.getInstance(project)
                .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME));
  }
}
