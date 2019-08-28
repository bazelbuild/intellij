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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspace.ModifiableModel;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.compiler.CachedTempFilesPool;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool;
import java.io.File;
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
   * <p>#api182: model API changed in 2018.3.
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

  // #api182: In 2018.3, addConfiguration only takes 2 or 4 parameters
  public static void addConfiguration(
      ModifiableModel workspaceModifiable,
      String id,
      String displayName,
      String shortDisplayName,
      File directory,
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages,
      Map<VirtualFile, PerFileCompilerOpts> configSourceFiles,
      CidrToolEnvironment toolEnvironment, // #api182
      WorkspaceFileMapper fileMapper // #api182
      ) {
    OCResolveConfigurationImpl.ModifiableModel config =
        workspaceModifiable.addConfiguration(
            id, displayName, null, OCResolveConfiguration.DEFAULT_FILE_SEPARATORS);
    for (Map.Entry<OCLanguageKind, PerLanguageCompilerOpts> languageEntry :
        configLanguages.entrySet()) {
      OCCompilerSettings.ModifiableModel langSettings =
          config.getLanguageCompilerSettings(languageEntry.getKey());
      PerLanguageCompilerOpts configForLanguage = languageEntry.getValue();
      langSettings.setCompiler(configForLanguage.kind, configForLanguage.compiler, directory);
      langSettings.setCompilerSwitches(configForLanguage.switches);
    }

    for (Map.Entry<VirtualFile, PerFileCompilerOpts> fileEntry : configSourceFiles.entrySet()) {
      PerFileCompilerOpts compilerOpts = fileEntry.getValue();
      OCCompilerSettings.ModifiableModel fileCompilerSettings =
          config.addSource(fileEntry.getKey(), compilerOpts.kind);
      fileCompilerSettings.setCompilerSwitches(compilerOpts.switches);
    }
  }

  public static ModifiableModel getClearedModifiableModel(Project project) {
    return OCWorkspaceImpl.getInstanceImpl(project).getModifiableModel(true);
  }

  /** Group compiler options for a specific file. #api182 */
  public static class PerFileCompilerOpts {
    final OCLanguageKind kind;
    final CidrCompilerSwitches switches;

    public PerFileCompilerOpts(OCLanguageKind kind, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.switches = switches;
    }
  }

  /** Group compiler options for a specific language. #api182 */
  public static class PerLanguageCompilerOpts {
    final OCCompilerKind kind;
    final File compiler;
    final CidrCompilerSwitches switches;

    public PerLanguageCompilerOpts(
        OCCompilerKind kind, File compiler, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.compiler = compiler;
      this.switches = switches;
    }
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
