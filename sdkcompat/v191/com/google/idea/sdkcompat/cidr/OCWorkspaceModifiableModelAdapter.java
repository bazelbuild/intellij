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
package com.google.idea.sdkcompat.cidr;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImplUtilKt;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Adapter to bridge different SDK versions. */
public class OCWorkspaceModifiableModelAdapter {
  private static final Logger logger = Logger.getInstance(OCWorkspaceModifiableModelAdapter.class);

  /**
   * Commits the modifiable model and returns any error messages encountered setting up the model
   * (e.g., while running a compiler for feature detection).
   *
   * <p>#api191: fileMapper unused in 2019.2.
   */
  public static ImmutableList<String> commit(
      OCWorkspaceImpl.ModifiableModel model,
      int serialVersion,
      CidrToolEnvironment toolEnvironment,
      WorkspaceFileMapper fileMapper) {
    ImmutableList<String> issues =
        collectCompilerSettingsInParallel(model, toolEnvironment, fileMapper);
    model.setClientVersion(serialVersion);
    model.preCommit();
    TransactionGuard.getInstance()
        .submitTransactionAndWait(
            () -> {
              ApplicationManager.getApplication().runWriteAction(model::commit);
            });
    return issues;
  }

  private static ImmutableList<String> collectCompilerSettingsInParallel(
      OCWorkspaceImpl.ModifiableModel model,
      CidrToolEnvironment toolEnvironment,
      WorkspaceFileMapper fileMapper) {
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    List<Future<List<Message>>> compilerSettingsTasks = new ArrayList<>();
    ExecutorService compilerSettingExecutor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "Compiler Settings Collector", Runtime.getRuntime().availableProcessors());
    for (OCResolveConfiguration.ModifiableModel config : model.getConfigurations()) {
      compilerSettingsTasks.add(
          OCWorkspaceImplUtilKt.collectCompilerSettingsAsync(
              config, toolEnvironment, compilerInfoCache, fileMapper, compilerSettingExecutor));
    }
    ImmutableList.Builder<String> issues = ImmutableList.builder();
    for (Future<List<Message>> task : compilerSettingsTasks) {
      try {
        task.get().stream()
            .filter(m -> m.getType().equals(Message.Type.ERROR))
            .map(Message::getText)
            .forEachOrdered(issues::add);
      } catch (InterruptedException e) {
        task.cancel(true);
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        task.cancel(true);
        logger.error("Error getting compiler settings, cancelling", e);
      }
    }
    return issues.build();
  }
}
