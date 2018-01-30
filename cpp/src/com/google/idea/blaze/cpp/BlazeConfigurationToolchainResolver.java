/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
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
final class BlazeConfigurationToolchainResolver {
  private static final Logger logger =
      Logger.getInstance(BlazeConfigurationToolchainResolver.class);

  private BlazeConfigurationToolchainResolver() {}

  /** Returns the toolchain used by each target */
  static ImmutableMap<TargetKey, CToolchainIdeInfo> buildToolchainLookupMap(
      BlazeContext context, TargetMap targetMap) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Build toolchain lookup map", EventType.Other));

          Map<TargetKey, CToolchainIdeInfo> toolchains = Maps.newLinkedHashMap();
          for (TargetIdeInfo target : targetMap.targets()) {
            CToolchainIdeInfo cToolchainIdeInfo = target.cToolchainIdeInfo;
            if (cToolchainIdeInfo != null) {
              toolchains.put(target.key, cToolchainIdeInfo);
            }
          }

          ImmutableMap.Builder<TargetKey, CToolchainIdeInfo> lookupTable = ImmutableMap.builder();
          for (TargetIdeInfo target : targetMap.targets()) {
            if (target.kind.languageClass != LanguageClass.C || target.kind == Kind.CC_TOOLCHAIN) {
              continue;
            }
            List<TargetKey> toolchainDeps =
                target
                    .dependencies
                    .stream()
                    .map(dep -> dep.targetKey)
                    .filter(toolchains::containsKey)
                    .collect(Collectors.toList());
            if (toolchainDeps.size() != 1) {
              issueToolchainWarning(context, target, toolchainDeps);
            }
            if (!toolchainDeps.isEmpty()) {
              TargetKey toolchainKey = toolchainDeps.get(0);
              CToolchainIdeInfo toolchainInfo = toolchains.get(toolchainKey);
              lookupTable.put(target.key, toolchainInfo);
            } else {
              CToolchainIdeInfo arbitraryToolchain = Iterables.getFirst(toolchains.values(), null);
              if (arbitraryToolchain != null) {
                lookupTable.put(target.key, arbitraryToolchain);
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
            target.key, toolchainDeps.size());
    if (usesAppleCcToolchain(target)) {
      logger.warn(warningMessage + " (apple_cc_toolchain)");
    } else {
      IssueOutput.warn(warningMessage).submit(context);
    }
  }

  private static boolean usesAppleCcToolchain(TargetIdeInfo target) {
    return target
        .dependencies
        .stream()
        .anyMatch(dep -> dep.targetKey.label.toString().startsWith("//tools/osx/crosstool"));
  }

  /** Returns the compiler settings for each toolchain. */
  static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> buildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      WorkspaceRoot workspaceRoot,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver pathResolver,
      CompilerInfoCache compilerInfoCache,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Build compiler settings map", EventType.Other));
          return doBuildCompilerSettingsMap(
              context,
              project,
              workspaceRoot,
              toolchainLookupMap,
              pathResolver,
              compilerInfoCache,
              oldCompilerSettings);
        });
  }

  private static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> doBuildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      WorkspaceRoot workspaceRoot,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver pathResolver,
      CompilerInfoCache compilerInfoCache,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings) {
    Set<CToolchainIdeInfo> toolchains =
        toolchainLookupMap.values().stream().distinct().collect(Collectors.toSet());
    List<ListenableFuture<Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings>>>
        compilerSettingsFutures = new ArrayList<>();
    for (CToolchainIdeInfo toolchain : toolchains) {
      compilerSettingsFutures.add(
          submit(
              () -> {
                File cppExecutable = pathResolver.resolveExecutionRootPath(toolchain.cppExecutable);
                if (cppExecutable == null) {
                  IssueOutput.error(
                          "Unable to find compiler executable: " + toolchain.cppExecutable)
                      .submit(context);
                  return null;
                }
                String compilerVersion =
                    CompilerVersionChecker.getInstance()
                        .checkCompilerVersion(workspaceRoot, cppExecutable);
                if (compilerVersion == null) {
                  IssueOutput.error("Unable to determine version of compiler " + cppExecutable)
                      .submit(context);
                  return null;
                }
                BlazeCompilerSettings oldSettings = oldCompilerSettings.get(toolchain);
                if (oldSettings != null
                    && oldSettings.getCompilerVersion().equals(compilerVersion)) {
                  return new SimpleImmutableEntry<>(toolchain, oldSettings);
                }
                BlazeCompilerSettings settings =
                    createBlazeCompilerSettings(
                        project, toolchain, cppExecutable, compilerVersion, compilerInfoCache);
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
  private static BlazeCompilerSettings createBlazeCompilerSettings(
      Project project,
      CToolchainIdeInfo toolchainIdeInfo,
      File cppExecutable,
      String compilerVersion,
      CompilerInfoCache compilerInfoCache) {
    File compilerWrapper = createCompilerExecutableWrapper(cppExecutable);
    if (compilerWrapper == null) {
      return null;
    }
    ImmutableList.Builder<String> cFlagsBuilder = ImmutableList.builder();
    cFlagsBuilder.addAll(toolchainIdeInfo.baseCompilerOptions);
    cFlagsBuilder.addAll(toolchainIdeInfo.cCompilerOptions);
    cFlagsBuilder.addAll(toolchainIdeInfo.unfilteredCompilerOptions);

    ImmutableList.Builder<String> cppFlagsBuilder = ImmutableList.builder();
    cppFlagsBuilder.addAll(toolchainIdeInfo.baseCompilerOptions);
    cppFlagsBuilder.addAll(toolchainIdeInfo.cppCompilerOptions);
    cppFlagsBuilder.addAll(toolchainIdeInfo.unfilteredCompilerOptions);
    return new BlazeCompilerSettings(
        project,
        compilerWrapper,
        compilerWrapper,
        cFlagsBuilder.build(),
        cppFlagsBuilder.build(),
        compilerVersion,
        compilerInfoCache);
  }

  /**
   * Create a wrapper script that transforms the CLion compiler invocation into a safe invocation of
   * the compiler script that blaze uses.
   *
   * <p>CLion passes arguments to the compiler in an arguments file. The c toolchain compiler
   * wrapper script doesn't handle arguments files, so we need to move the compiler arguments from
   * the file to the command line.
   *
   * @param blazeCompilerExecutableFile blaze compiler wrapper
   * @return The wrapper script that CLion can call.
   */
  @Nullable
  private static File createCompilerExecutableWrapper(File blazeCompilerExecutableFile) {
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
              "$EXE $ARGS $2");

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
