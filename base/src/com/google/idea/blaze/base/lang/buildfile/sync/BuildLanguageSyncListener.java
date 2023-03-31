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
package com.google.idea.blaze.base.lang.buildfile.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.protobuf.InvalidProtocolBufferException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * {@link SyncListener} for BUILD language support. After syncs complete, retrieves BUILD language
 * spec via invocation of `info build-language`. Only used in query-sync
 */
public class BuildLanguageSyncListener implements SyncListener {
  private static final Logger logger = Logger.getInstance(BuildLanguageSyncListener.class);

  @Override
  public void afterSync(Project project, BlazeContext context) {
    BuildLanguageSpecService buildLanguageSpecProvider =
        project.getService(BuildLanguageSpecService.class);
    if (buildLanguageSpecProvider == null) {
      logger.error("BuildLanguageSpecProvider service unavailable");
    } else if (buildLanguageSpecProvider.shouldFetchLanguageSpec()) {
      BuildLanguageSpec buildLanguageSpec = getBuildLanguageSpec(project, context);
      if (buildLanguageSpec != null) {
        buildLanguageSpecProvider.setLanguageSpec(buildLanguageSpec);
      }
    }
  }

  @SuppressWarnings("ProtoParseWithRegistry")
  @Nullable
  private BuildLanguageSpec getBuildLanguageSpec(Project project, BlazeContext context) {
    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getDefaultInvoker(project, context);
    try {
      ListenableFuture<byte[]> future =
          BlazeInfoRunner.getInstance()
              .runBlazeInfoGetBytes(
                  project, invoker, context, ImmutableList.of(), BlazeInfo.BUILD_LANGUAGE);

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
