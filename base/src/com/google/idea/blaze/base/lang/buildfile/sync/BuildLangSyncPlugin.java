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
package com.google.idea.blaze.base.lang.buildfile.sync;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.protobuf.repackaged.InvalidProtocolBufferException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Updates the language specification during the blaze sync process */
public class BuildLangSyncPlugin implements BlazeSyncPlugin {

  private static final Logger logger = Logger.getInstance(BuildLangSyncPlugin.class);

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
      @Nullable SyncState previousSyncState) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    LanguageSpecResult spec =
        getBuildLanguageSpec(project, workspaceRoot, projectViewSet, previousSyncState, context);
    if (spec != null) {
      syncStateBuilder.put(LanguageSpecResult.class, spec);
    }
  }

  @Nullable
  private static LanguageSpecResult getBuildLanguageSpec(
      Project project,
      WorkspaceRoot workspace,
      ProjectViewSet projectViewSet,
      @Nullable SyncState previousSyncState,
      BlazeContext parentContext) {
    LanguageSpecResult oldResult =
        previousSyncState != null ? previousSyncState.get(LanguageSpecResult.class) : null;
    if (oldResult != null && !oldResult.shouldRecalculateSpec()) {
      return oldResult;
    }
    LanguageSpecResult result =
        Scope.push(
            parentContext,
            (context) -> {
              context.push(new TimingScope("BUILD language spec", EventType.BlazeInvocation));
              BuildLanguageSpec spec =
                  parseLanguageSpec(project, workspace, projectViewSet, context);
              if (spec != null) {
                return new LanguageSpecResult(spec, System.currentTimeMillis());
              }
              return null;
            });
    return result != null ? result : oldResult;
  }

  @Nullable
  private static BuildLanguageSpec parseLanguageSpec(
      Project project,
      WorkspaceRoot workspace,
      ProjectViewSet projectViewSet,
      BlazeContext context) {
    try {
      ListenableFuture<byte[]> future =
          BlazeInfoRunner.getInstance()
              .runBlazeInfoGetBytes(
                  context,
                  Blaze.getBuildSystemProvider(project).getSyncBinaryPath(),
                  workspace,
                  BlazeFlags.blazeFlags(
                      project,
                      projectViewSet,
                      BlazeCommandName.INFO,
                      BlazeInvocationContext.Sync,
                      null),
                  BlazeInfo.BUILD_LANGUAGE);

      return BuildLanguageSpec.fromProto(Build.BuildLanguage.parseFrom(future.get()));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException | InvalidProtocolBufferException | NullPointerException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        logger.error(e);
      }
      return null;
    } catch (Throwable e) {
      logger.error(e);
      return null;
    }
  }
}
