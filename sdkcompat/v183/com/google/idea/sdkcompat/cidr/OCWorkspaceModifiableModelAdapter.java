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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration.ModifiableModel.Message;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspace.ModifiableModel;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
   * <p>#api182: model API changed in 2018.3.
   */
  public static ImmutableList<String> commit(
      OCWorkspaceImpl.ModifiableModel model,
      int serialVersion,
      CidrToolEnvironment toolEnvironment,
      NullableFunction<File, VirtualFile> fileMapper) {
    ImmutableList<String> issues =
        collectCompilerSettingsInParallel(model, toolEnvironment, fileMapper);
    model.setSourceVersion(serialVersion);
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
      NullableFunction<File, VirtualFile> fileMapper // #api182
      ) {
    OCResolveConfigurationImpl.ModifiableModel config =
        workspaceModifiable.addConfiguration(
            id, displayName, shortDisplayName, OCResolveConfiguration.DEFAULT_FILE_SEPARATORS);
    for (Map.Entry<OCLanguageKind, PerLanguageCompilerOpts> languageEntry :
        configLanguages.entrySet()) {
      OCCompilerSettings.ModifiableModel langSettings =
          config.getLanguageCompilerSettings(languageEntry.getKey());
      PerLanguageCompilerOpts configForLanguage = languageEntry.getValue();
      langSettings.setCompiler(configForLanguage.kind, configForLanguage.compiler, directory);
      langSettings.setSwitches(configForLanguage.switches);
    }

    for (Map.Entry<VirtualFile, PerFileCompilerOpts> fileEntry : configSourceFiles.entrySet()) {
      PerFileCompilerOpts compilerOpts = fileEntry.getValue();
      OCCompilerSettings.ModifiableModel fileCompilerSettings =
          config.addSource(fileEntry.getKey(), compilerOpts.kind);
      fileCompilerSettings.setSwitches(compilerOpts.switches);
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
      OCWorkspaceImpl.ModifiableModel model,
      CidrToolEnvironment toolEnvironment,
      NullableFunction<File, VirtualFile> fileMapper) {
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    List<Future<List<Message>>> compilerSettingsTasks = new ArrayList<>();
    ExecutorService compilerSettingExecutor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "Compiler Settings Collector", Runtime.getRuntime().availableProcessors());
    for (OCResolveConfiguration.ModifiableModel config : model.getConfigurations()) {
      compilerSettingsTasks.add(
          compilerSettingExecutor.submit(
              () ->
                  config.collectCompilerSettings(toolEnvironment, compilerInfoCache, fileMapper)));
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
