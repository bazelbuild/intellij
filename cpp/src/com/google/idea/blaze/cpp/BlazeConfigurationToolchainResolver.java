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
package com.google.idea.blaze.cpp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.cpp.CompilerVersionChecker.VersionCheckException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Converts {@link CToolchainIdeInfo} to interfaces used by {@link
 * com.jetbrains.cidr.lang.workspace.OCResolveConfiguration}
 */
public final class BlazeConfigurationToolchainResolver {
  private static final Logger logger =
      Logger.getInstance(BlazeConfigurationToolchainResolver.class);

  private BlazeConfigurationToolchainResolver() {}

  /** Returns the C toolchain used by each C target */
  @VisibleForTesting
  public static ImmutableMap<TargetKey, CToolchainIdeInfo> buildToolchainLookupMap(
      BlazeContext context, TargetMap targetMap) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Build toolchain lookup map", EventType.Other));

          Map<TargetKey, CToolchainIdeInfo> toolchains = Maps.newLinkedHashMap();
          for (TargetIdeInfo target : targetMap.targets()) {
            CToolchainIdeInfo cToolchainIdeInfo = target.getcToolchainIdeInfo();
            if (cToolchainIdeInfo != null) {
              toolchains.put(target.getKey(), cToolchainIdeInfo);
            }
          }

          ImmutableMap.Builder<TargetKey, CToolchainIdeInfo> lookupTable = ImmutableMap.builder();
          for (TargetIdeInfo target : targetMap.targets()) {
            if (target.getKind().languageClass != LanguageClass.C
                || target.getKind() == Kind.CC_TOOLCHAIN) {
              continue;
            }
            List<TargetKey> toolchainDeps =
                target.getDependencies().stream()
                    .map(Dependency::getTargetKey)
                    .filter(toolchains::containsKey)
                    .collect(Collectors.toList());
            if (toolchainDeps.size() != 1) {
              issueToolchainWarning(context, target, toolchainDeps);
            }
            if (!toolchainDeps.isEmpty()) {
              TargetKey toolchainKey = toolchainDeps.get(0);
              CToolchainIdeInfo toolchainInfo = toolchains.get(toolchainKey);
              lookupTable.put(target.getKey(), toolchainInfo);
            } else {
              CToolchainIdeInfo arbitraryToolchain = Iterables.getFirst(toolchains.values(), null);
              if (arbitraryToolchain != null) {
                lookupTable.put(target.getKey(), arbitraryToolchain);
              }
            }
          }
          return lookupTable.build();
        });
  }

  private static void issueToolchainWarning(
      BlazeContext context, TargetIdeInfo target, List<TargetKey> toolchainDeps) {
    String warningMessage =
        String.format(
            "cc target %s does not depend on exactly 1 cc toolchain. " + " Found %d toolchains.",
            target.getKey(), toolchainDeps.size());
    if (usesAppleCcToolchain(target)) {
      logger.warn(warningMessage + " (apple_cc_toolchain)");
    } else {
      IssueOutput.warn(warningMessage).submit(context);
    }
  }

  private static boolean usesAppleCcToolchain(TargetIdeInfo target) {
    return target.getDependencies().stream()
        .map(Dependency::getTargetKey)
        .map(TargetKey::getLabel)
        .map(TargetExpression::toString)
        .anyMatch(s -> s.startsWith("//tools/osx/crosstool"));
  }

  /** Returns the compiler settings for each toolchain. */
  static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> buildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver executionRootPathResolver,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Build compiler settings map", EventType.Other));
          return doBuildCompilerSettingsMap(
              context, project, toolchainLookupMap, executionRootPathResolver, oldCompilerSettings);
        });
  }

  private static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> doBuildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver executionRootPathResolver,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings) {
    Set<CToolchainIdeInfo> toolchains = new HashSet<>(toolchainLookupMap.values());
    List<ListenableFuture<Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings>>>
        compilerSettingsFutures = new ArrayList<>();
    for (CToolchainIdeInfo toolchain : toolchains) {
      compilerSettingsFutures.add(
          submit(
              () -> {
                File cppExecutable =
                    executionRootPathResolver.resolveExecutionRootPath(
                        toolchain.getCppExecutable());
                if (cppExecutable == null) {
                  IssueOutput.error(
                          "Unable to find compiler executable: " + toolchain.getCppExecutable())
                      .submit(context);
                  return null;
                }
                String compilerVersion =
                    getCompilerVersion(project, context, executionRootPathResolver, cppExecutable);
                if (compilerVersion == null) {
                  return null;
                }
                BlazeCompilerSettings oldSettings = oldCompilerSettings.get(toolchain);
                if (oldSettings != null
                    && oldSettings.getCompilerVersion().equals(compilerVersion)) {
                  return new SimpleImmutableEntry<>(toolchain, oldSettings);
                }
                BlazeCompilerSettings settings =
                    createBlazeCompilerSettings(
                        project,
                        toolchain,
                        executionRootPathResolver.getExecutionRoot(),
                        cppExecutable,
                        compilerVersion);
                if (settings == null) {
                  IssueOutput.error("Unable to create compiler wrapper for: " + cppExecutable)
                      .submit(context);
                  return null;
                }
                return new SimpleImmutableEntry<>(toolchain, settings);
              }));
    }
    ImmutableMap.Builder<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettingsMap =
        ImmutableMap.builder();
    try {
      List<Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings>> createdSettings =
          Futures.allAsList(compilerSettingsFutures).get();
      for (Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings> createdSetting : createdSettings) {
        if (createdSetting != null) {
          compilerSettingsMap.put(createdSetting);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error("Could not build C compiler settings map: " + e).submit(context);
    }
    return compilerSettingsMap.build();
  }

  @Nullable
  private static String getCompilerVersion(
      Project project,
      BlazeContext context,
      ExecutionRootPathResolver executionRootPathResolver,
      File cppExecutable) {
    File executionRoot = executionRootPathResolver.getExecutionRoot();
    try {
      return CompilerVersionChecker.getInstance()
          .checkCompilerVersion(executionRoot, cppExecutable);
    } catch (VersionCheckException e) {
      switch (e.kind) {
        case MISSING_EXEC_ROOT:
          IssueOutput.error(
                  String.format(
                      "Missing execution root %s (checking compiler).\n"
                          + "Double-click to run sync and create the execution root.",
                      executionRoot.getAbsolutePath()))
              .navigatable(
                  new NavigatableAdapter() {
                    @Override
                    public void navigate(boolean requestFocus) {
                      BlazeSyncManager.getInstance(project).incrementalProjectSync();
                    }
                  })
              .submit(context);
          return null;
        case MISSING_COMPILER:
          IssueOutput.error(
                  String.format(
                      "Unable to access compiler executable \"%s\".\n"
                          + "Check if it is accessible from the cmdline.",
                      cppExecutable.getAbsolutePath()))
              .submit(context);
          return null;
        case GENERIC_FAILURE:
          IssueOutput.error(
                  String.format(
                      "Unable to check compiler version for \"%s\".\n%s\n"
                          + "Check if running the compiler with --version works on the cmdline.",
                      cppExecutable.getAbsolutePath(), e.getMessage()))
              .submit(context);
          return null;
      }
      return null;
    }
  }

  @Nullable
  private static BlazeCompilerSettings createBlazeCompilerSettings(
      Project project,
      CToolchainIdeInfo toolchainIdeInfo,
      File executionRoot,
      File cppExecutable,
      String compilerVersion) {
    File compilerWrapper = createCompilerExecutableWrapper(executionRoot, cppExecutable);
    if (compilerWrapper == null) {
      return null;
    }
    ImmutableList.Builder<String> cFlagsBuilder = ImmutableList.builder();
    cFlagsBuilder.addAll(toolchainIdeInfo.getBaseCompilerOptions());
    cFlagsBuilder.addAll(toolchainIdeInfo.getCppCompilerOptions());
    cFlagsBuilder.addAll(toolchainIdeInfo.getUnfilteredCompilerOptions());

    ImmutableList.Builder<String> cppFlagsBuilder = ImmutableList.builder();
    cppFlagsBuilder.addAll(toolchainIdeInfo.getBaseCompilerOptions());
    cppFlagsBuilder.addAll(toolchainIdeInfo.getCppCompilerOptions());
    cppFlagsBuilder.addAll(toolchainIdeInfo.getUnfilteredCompilerOptions());
    return new BlazeCompilerSettings(
        project,
        compilerWrapper,
        compilerWrapper,
        cFlagsBuilder.build(),
        cppFlagsBuilder.build(),
        compilerVersion);
  }

  /**
   * Create a wrapper script that transforms the CLion compiler invocation into a safe invocation of
   * the compiler script that blaze uses.
   *
   * <p>CLion passes arguments to the compiler in an arguments file. The c toolchain compiler
   * wrapper script doesn't handle arguments files, so we need to move the compiler arguments from
   * the file to the command line.
   *
   * @param executionRoot the execution root for running the compiler
   * @param blazeCompilerExecutableFile the compiler
   * @return The wrapper script that CLion can call.
   */
  @Nullable
  private static File createCompilerExecutableWrapper(
      File executionRoot, File blazeCompilerExecutableFile) {
    try {
      File blazeCompilerWrapper =
          FileUtil.createTempFile("blaze_compiler", ".sh", true /* deleteOnExit */);
      if (!blazeCompilerWrapper.setExecutable(true)) {
        logger.warn("Unable to make compiler wrapper script executable: " + blazeCompilerWrapper);
        return null;
      }
      ImmutableList<String> compilerWrapperScriptLines =
          ImmutableList.of(
              "#!/bin/bash",
              "",
              "# The c toolchain compiler wrapper script doesn't handle arguments files, so we",
              "# need to move the compiler arguments from the file to the command line.",
              "",
              "if [ $# -ne 2 ]; then",
              "  echo \"Usage: $0 @arg-file compile-file\"",
              "  exit 2;",
              "fi",
              "",
              "if [[ $1 != @* ]]; then",
              "  echo \"Usage: $0 @arg-file compile-file\"",
              "  exit 3;",
              "fi",
              "",
              " # Remove the @ before the arguments file path",
              "ARG_FILE=${1#@}",
              "# The actual compiler wrapper script we get from blaze",
              "EXE=" + blazeCompilerExecutableFile.getPath(),
              "# Read in the arguments file so we can pass the arguments on the command line.",
              "ARGS=`cat $ARG_FILE`",
              String.format("(cd %s && $EXE $ARGS $2)", executionRoot));

      try (PrintWriter pw = new PrintWriter(blazeCompilerWrapper, UTF_8.name())) {
        compilerWrapperScriptLines.forEach(pw::println);
      }
      return blazeCompilerWrapper;
    } catch (IOException e) {
      logger.warn(
          "Unable to write compiler wrapper script executable: " + blazeCompilerExecutableFile, e);
      return null;
    }
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }
}
