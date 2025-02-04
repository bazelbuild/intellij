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
package com.google.idea.blaze.base.model;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.FutureUtil.FutureResult;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;

import java.util.List;

public class ExternalWorkspaceQuerySync {

  public static void syncExternalWorkspace(Project project, BlazeContext context, ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) throws BuildException {
    if (Blaze.getProjectType(project) != BlazeImportSettings.ProjectType.QUERY_SYNC) {
      return;
    }

    BuildSystem.BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
    try {
      var data = getExternalWorkspaceData(
          project,
          context,
          projectViewSet,
          blazeProjectData.getBlazeVersionData(),
          invoker.getBlazeInfo()
      );
      ExternalWorkspaceDataManager.getInstance(project).setData(data);
    } catch (SyncFailedException e) {
      throw new BuildException(e);
    }

  }

  private static ExternalWorkspaceData getExternalWorkspaceData(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeInfo blazeInfo)
      throws BuildException {

    List<String> blazeModFlags =
        BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            BlazeCommandName.MOD,
            context,
            BlazeInvocationContext.SYNC_CONTEXT);

    ListenableFuture<ExternalWorkspaceData> externalWorkspaceDataFuture =
        ExternalWorkspaceDataProvider.getInstance(project)
            .getExternalWorkspaceData(context, blazeModFlags, blazeVersionData, blazeInfo);

    FutureResult<ExternalWorkspaceData> externalWorkspaceDataResult =
        FutureUtil.waitForFuture(context, externalWorkspaceDataFuture)
            .timed(Blaze.buildSystemName(project) + "Mod", EventType.BlazeInvocation)
            .withProgressMessage("Resolving module repository mapping...")
            .onError(String.format("Could not run %s mod dump_repo_mapping", Blaze.buildSystemName(project)))
            .run();

    ExternalWorkspaceData externalWorkspaceData = externalWorkspaceDataResult.result();
    if (externalWorkspaceData == null) {
      Exception exception = externalWorkspaceDataResult.exception();
      if (exception != null) {
        throw new BuildException(exception);
      }
      throw new BuildException("Sync failed: no external workspace data returned");
    }

    return externalWorkspaceData;
  }

}
