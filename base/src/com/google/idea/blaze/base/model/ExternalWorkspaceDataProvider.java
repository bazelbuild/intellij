/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.mod.BlazeModException;
import com.google.idea.blaze.base.command.mod.BlazeModRunner;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class ExternalWorkspaceDataProvider {

  // bazel mod dump_repo_mapping was added in bazel 7.1.0
  private static final BazelVersion MINIMUM_BLAZE_VERSION = new BazelVersion(7, 1, 0);

  private static final Logger logger = Logger.getInstance(ExternalWorkspaceDataProvider.class);
  private final Project project;

  private volatile ExternalWorkspaceData externalWorkspaceData;

  public ExternalWorkspaceDataProvider(Project project) {
    this.project = project;
  }

  public static ExternalWorkspaceDataProvider getInstance(Project project) {
    return project.getService(ExternalWorkspaceDataProvider.class);
  }

  static Boolean isEnabled(BlazeVersionData blazeVersionData) {
    if (!Registry.is("bazel.read.external.workspace.data")) {
      logger.info("disabled by registry");
      return false;
    }

    return blazeVersionData.bazelIsAtLeastVersion(MINIMUM_BLAZE_VERSION);
  }

  public ListenableFuture<ExternalWorkspaceData> getExternalWorkspaceData(
      BlazeContext context,
      List<String> blazeFlags,
      BlazeVersionData blazeVersionData,
      BlazeInfo blazeInfo
  ) {
    // check minimum bazel version
    if (!isEnabled(blazeVersionData)) {
      return Futures.immediateFuture(ExternalWorkspaceData.EMPTY);
    }

    // validate that bzlmod is enabled (technically this validates that the --enable_bzlmod is not
    // changed from the default `true` aka set to false)
    String starLarkSemantics = blazeInfo.getStarlarkSemantics();
    if (starLarkSemantics == null || starLarkSemantics.isEmpty() || starLarkSemantics.contains("enable_bzlmod=false")) {
      return Futures.immediateFuture(ExternalWorkspaceData.EMPTY);
    }

    return BlazeExecutor.getInstance().submit(() -> {
      ExternalWorkspaceData mapping = getCachedExternalWorkspaceData(context, blazeFlags);
      if (mapping == null) {
        throw new BlazeModException("Unable to get module mapping");
      }
      return mapping;
    });
  }

  private @Nullable ExternalWorkspaceData getCachedExternalWorkspaceData(
      BlazeContext context,
      List<String> blazeFlags) {
    if (externalWorkspaceData != null) {
      logger.info("Using cached External Repository Mapping");
      return externalWorkspaceData;
    }
    try {
      BlazeImportSettings importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
      if (importSettings == null) {
        return null;
      }
      BuildSystem.BuildInvoker buildInvoker =
          Blaze.getBuildSystemProvider(project)
              .getBuildSystem()
              .getDefaultInvoker(project, context);

      externalWorkspaceData = BlazeModRunner.getInstance()
                                  .dumpRepoMapping(
                                      project,
                                      buildInvoker,
                                      context,
                                      importSettings.getBuildSystem(),
                                      blazeFlags
                                  ).get();
      return externalWorkspaceData;
    } catch (InterruptedException | ExecutionException e) {
      logger.warn("Unable to run blaze mod dump_repo_mapping", e);
      return null;
    }
  }

  public void invalidate(BlazeContext context, SyncMode syncMode) {
    logger.info("Invalidating External Repository Mapping info");
    context.output(new StatusOutput(String.format("Invalidating External Repository Mapping info (%s)", syncMode)));
    externalWorkspaceData = null;
  }

  public static final class Invalidator implements SyncListener {
    @Override
    public void onSyncStart(
        Project project,
        BlazeContext context,
        SyncMode syncMode) {
      if (syncMode == SyncMode.FULL) {
        ExternalWorkspaceDataProvider provider = ExternalWorkspaceDataProvider.getInstance(project);
        if (provider != null) {
          provider.invalidate(context, syncMode);
        }
      }
    }
  }
}
