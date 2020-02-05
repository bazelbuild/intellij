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
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.compiler.CachedTempFilesPool;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

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
    ImmutableList<String> issues = collectCompilerSettingsInParallel(model, toolEnvironment);
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
      OCWorkspaceImpl.ModifiableModel model, CidrToolEnvironment toolEnvironment) {
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    TempFilesPool tempFilesPool = new CachedTempFilesPool();
    Session<Integer> session = compilerInfoCache.createSession(new EmptyProgressIndicator());
    ImmutableList.Builder<String> issues = ImmutableList.builder();
    try {
      int i = 0;
      for (OCResolveConfiguration.ModifiableModel config : model.getConfigurations()) {
        session.schedule(i++, config, toolEnvironment);
      }
      MultiMap<Integer, Message> messages = new MultiMap<>();
      session.waitForAll(messages);
      for (Map.Entry<Integer, Collection<Message>> entry :
          ContainerUtil.sorted(messages.entrySet(), Comparator.comparingInt(Map.Entry::getKey))) {
        entry.getValue().stream()
            .filter(m -> m.getType().equals(Message.Type.ERROR))
            .map(Message::getText)
            .forEachOrdered(issues::add);
      }
    } catch (Error | RuntimeException e) {
      session.dispose(); // This calls tempFilesPool.clean();
      throw e;
    }
    tempFilesPool.clean();
    return issues.build();
  }
}
