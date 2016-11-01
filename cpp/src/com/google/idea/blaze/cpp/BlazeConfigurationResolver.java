/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

final class BlazeConfigurationResolver {
  private static final class MapEntry {
    public final RuleKey ruleKey;
    public final BlazeResolveConfiguration configuration;

    public MapEntry(RuleKey ruleKey, BlazeResolveConfiguration configuration) {
      this.ruleKey = ruleKey;
      this.configuration = configuration;
    }
  }

  private static final Logger LOG = Logger.getInstance(BlazeConfigurationResolver.class);
  private final Project project;

  private ImmutableMap<RuleKey, BlazeResolveConfiguration> resolveConfigurations =
      ImmutableMap.of();

  public BlazeConfigurationResolver(Project project) {
    this.project = project;
  }

  public void update(BlazeContext context, BlazeProjectData blazeProjectData) {
    WorkspacePathResolver workspacePathResolver = blazeProjectData.workspacePathResolver;
    ImmutableMap<RuleKey, CToolchainIdeInfo> toolchainLookupMap =
        BlazeResolveConfiguration.buildToolchainLookupMap(
            context, blazeProjectData.ruleMap, blazeProjectData.reverseDependencies);
    resolveConfigurations =
        buildBlazeConfigurationMap(
            context, blazeProjectData, toolchainLookupMap, workspacePathResolver);
  }

  private ImmutableMap<RuleKey, BlazeResolveConfiguration> buildBlazeConfigurationMap(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<RuleKey, CToolchainIdeInfo> toolchainLookupMap,
      WorkspacePathResolver workspacePathResolver) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableMap<RuleKey, BlazeResolveConfiguration>>)
            context -> {
              context.push(new TimingScope("Build C configuration map"));

              ConcurrentMap<CToolchainIdeInfo, File> compilerWrapperCache = Maps.newConcurrentMap();
              List<ListenableFuture<MapEntry>> mapEntryFutures = Lists.newArrayList();

              for (RuleIdeInfo rule : blazeProjectData.ruleMap.rules()) {
                if (rule.kind.getLanguageClass() == LanguageClass.C) {
                  ListenableFuture<MapEntry> future =
                      submit(
                          () ->
                              createResolveConfiguration(
                                  rule,
                                  toolchainLookupMap,
                                  compilerWrapperCache,
                                  workspacePathResolver,
                                  blazeProjectData));
                  mapEntryFutures.add(future);
                }
              }

              ImmutableMap.Builder<RuleKey, BlazeResolveConfiguration> newResolveConfigurations =
                  ImmutableMap.builder();
              List<MapEntry> mapEntries;
              try {
                mapEntries = Futures.allAsList(mapEntryFutures).get();
              } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Could not build C resolve configurations", e);
                context.setCancelled();
                return ImmutableMap.of();
              }

              for (MapEntry mapEntry : mapEntries) {
                // Skip over labels that don't have C configuration data.
                if (mapEntry != null) {
                  newResolveConfigurations.put(mapEntry.ruleKey, mapEntry.configuration);
                }
              }
              return newResolveConfigurations.build();
            });
  }

  private static ListenableFuture<MapEntry> submit(Callable<MapEntry> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  @Nullable
  private MapEntry createResolveConfiguration(
      RuleIdeInfo rule,
      ImmutableMap<RuleKey, CToolchainIdeInfo> toolchainLookupMap,
      ConcurrentMap<CToolchainIdeInfo, File> compilerWrapperCache,
      WorkspacePathResolver workspacePathResolver,
      BlazeProjectData blazeProjectData) {
    RuleKey ruleKey = rule.key;

    CToolchainIdeInfo toolchainIdeInfo = toolchainLookupMap.get(ruleKey);
    if (toolchainIdeInfo != null) {
      File compilerWrapper =
          findOrCreateCompilerWrapperScript(
              compilerWrapperCache, toolchainIdeInfo, workspacePathResolver, ruleKey);
      if (compilerWrapper != null) {
        BlazeResolveConfiguration config =
            BlazeResolveConfiguration.createConfigurationForTarget(
                project,
                workspacePathResolver,
                blazeProjectData.ruleMap.get(ruleKey),
                toolchainIdeInfo,
                compilerWrapper);
        if (config != null) {
          return new MapEntry(ruleKey, config);
        }
      }
    }
    return null;
  }

  @Nullable
  private static File findOrCreateCompilerWrapperScript(
      Map<CToolchainIdeInfo, File> compilerWrapperCache,
      CToolchainIdeInfo toolchainIdeInfo,
      WorkspacePathResolver workspacePathResolver,
      RuleKey ruleKey) {
    File compilerWrapper = compilerWrapperCache.get(toolchainIdeInfo);
    if (compilerWrapper == null) {
      File cppExecutable = toolchainIdeInfo.cppExecutable.getAbsoluteOrRelativeFile();
      if (cppExecutable != null && !cppExecutable.isAbsolute()) {
        cppExecutable = workspacePathResolver.resolveToFile(cppExecutable.getPath());
      }
      if (cppExecutable == null) {
        String errorMessage =
            String.format(
                "Unable to find compiler executable: %s for rule %s",
                toolchainIdeInfo.cppExecutable.toString(), ruleKey);
        LOG.warn(errorMessage);
        compilerWrapper = null;
      } else {
        compilerWrapper = createCompilerExecutableWrapper(cppExecutable);
        if (compilerWrapper != null) {
          compilerWrapperCache.put(toolchainIdeInfo, compilerWrapper);
        }
      }
    }
    return compilerWrapper;
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

      try (PrintWriter pw = new PrintWriter(blazeCompilerWrapper)) {
        compilerWrapperScriptLines.forEach(pw::println);
      }
      return blazeCompilerWrapper;
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  public OCResolveConfiguration getConfigurationForFile(VirtualFile sourceFile) {
    SourceToRuleMap sourceToRuleMap = SourceToRuleMap.getInstance(project);
    List<RuleKey> targetsForSourceFile =
        Lists.newArrayList(
            sourceToRuleMap.getRulesForSourceFile(VfsUtilCore.virtualToIoFile(sourceFile)));
    if (targetsForSourceFile.isEmpty()) {
      return null;
    }

    // If a source file is in two different targets,
    // we can't possibly show how it will be interpreted in both contexts at the same time
    // in the IDE, so just pick the first target after we sort.
    targetsForSourceFile.sort((o1, o2) -> o1.toString().compareTo(o2.toString()));
    RuleKey ruleKey = Iterables.getFirst(targetsForSourceFile, null);
    assert (ruleKey != null);

    return resolveConfigurations.get(ruleKey);
  }

  public List<? extends OCResolveConfiguration> getAllConfigurations() {
    return ImmutableList.copyOf(resolveConfigurations.values());
  }
}
