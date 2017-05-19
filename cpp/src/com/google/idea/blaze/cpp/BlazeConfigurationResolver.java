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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class BlazeConfigurationResolver {
  private static final class MapEntry {
    public final TargetKey targetKey;
    public final BlazeResolveConfiguration configuration;

    public MapEntry(TargetKey targetKey, BlazeResolveConfiguration configuration) {
      this.targetKey = targetKey;
      this.configuration = configuration;
    }
  }

  private static final Logger LOG = Logger.getInstance(BlazeConfigurationResolver.class);
  private final Project project;

  private ImmutableMap<TargetKey, BlazeResolveConfiguration> resolveConfigurations =
      ImmutableMap.of();

  public BlazeConfigurationResolver(Project project) {
    this.project = project;
  }

  public void update(BlazeContext context, BlazeProjectData blazeProjectData) {
    ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap =
        BlazeResolveConfiguration.buildToolchainLookupMap(context, blazeProjectData.targetMap);
    ImmutableMap<File, VirtualFile> headerRoots =
        collectHeaderRoots(context, blazeProjectData, toolchainLookupMap);
    ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings =
        buildCompilerSettingsMap(
            context, project, toolchainLookupMap, blazeProjectData.workspacePathResolver);
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    resolveConfigurations =
        buildBlazeConfigurationMap(
            context,
            blazeProjectData,
            toolchainLookupMap,
            headerRoots,
            compilerSettings,
            compilerInfoCache);
  }

  private ImmutableMap<File, VirtualFile> collectHeaderRoots(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableMap<File, VirtualFile>>)
            context -> {
              context.push(new TimingScope("Resolve header include roots"));
              Set<ExecutionRootPath> paths =
                  collectExecutionRootPaths(blazeProjectData.targetMap, toolchainLookupMap);
              return doCollectHeaderRoots(context, blazeProjectData, paths);
            });
  }

  private ImmutableMap<File, VirtualFile> doCollectHeaderRoots(
      BlazeContext context, BlazeProjectData projectData, Set<ExecutionRootPath> rootPaths) {
    ExecutionRootPathResolver pathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystem(project),
            WorkspaceRoot.fromProject(project),
            projectData.blazeInfo.getExecutionRoot(),
            projectData.workspacePathResolver);
    ConcurrentMap<File, VirtualFile> rootsMap = Maps.newConcurrentMap();
    List<ListenableFuture<Void>> futures = Lists.newArrayListWithCapacity(rootPaths.size());
    for (ExecutionRootPath path : rootPaths) {
      futures.add(
          submit(
              () -> {
                ImmutableList<File> possibleDirectories =
                    pathResolver.resolveToIncludeDirectories(path);
                for (File file : possibleDirectories) {
                  VirtualFile vf = getVirtualFile(file);
                  if (vf != null) {
                    rootsMap.put(file, vf);
                  } else if (!isOutputArtifact(projectData.blazeInfo, path)
                      && FileAttributeProvider.getInstance().exists(file)) {
                    // If it's not a blaze output file, we expect it to always resolve.
                    LOG.info(String.format("Unresolved header root %s", file.getAbsolutePath()));
                  }
                }
                return null;
              }));
    }
    try {
      Futures.allAsList(futures).get();
      return ImmutableMap.copyOf(rootsMap);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error("Error resolving header include roots: " + e).submit(context);
      LOG.error("Error resolving header include roots", e);
    }
    return ImmutableMap.of();
  }

  private static boolean isOutputArtifact(BlazeInfo blazeInfo, ExecutionRootPath path) {
    return ExecutionRootPath.isAncestor(blazeInfo.getBlazeGenfilesExecutionRootPath(), path, false)
        || ExecutionRootPath.isAncestor(blazeInfo.getBlazeBinExecutionRootPath(), path, false);
  }

  private static Set<ExecutionRootPath> collectExecutionRootPaths(
      TargetMap targetMap, ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap) {
    Set<ExecutionRootPath> paths = Sets.newHashSet();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (target.cIdeInfo != null) {
        paths.addAll(target.cIdeInfo.localIncludeDirectories);
        paths.addAll(target.cIdeInfo.transitiveSystemIncludeDirectories);
        paths.addAll(target.cIdeInfo.transitiveIncludeDirectories);
        paths.addAll(target.cIdeInfo.transitiveQuoteIncludeDirectories);
      }
    }
    Set<CToolchainIdeInfo> toolchains = new LinkedHashSet<>(toolchainLookupMap.values());
    for (CToolchainIdeInfo toolchain : toolchains) {
      paths.addAll(toolchain.builtInIncludeDirectories);
      paths.addAll(toolchain.unfilteredToolchainSystemIncludes);
    }
    return paths;
  }

  @Nullable
  private static VirtualFile getVirtualFile(File file) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile vf = fileSystem.findFileByPathIfCached(file.getPath());
    if (vf == null) {
      vf = fileSystem.findFileByIoFile(file);
    }
    return vf;
  }

  private ImmutableMap<TargetKey, BlazeResolveConfiguration> buildBlazeConfigurationMap(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<File, VirtualFile> headerRoots,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings,
      CompilerInfoCache compilerInfoCache) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableMap<TargetKey, BlazeResolveConfiguration>>)
            context -> {
              context.push(new TimingScope("Build C configuration map"));

              List<ListenableFuture<MapEntry>> mapEntryFutures = Lists.newArrayList();

              for (TargetIdeInfo target : blazeProjectData.targetMap.targets()) {
                if (target.kind.getLanguageClass() == LanguageClass.C
                    && target.kind != Kind.CC_TOOLCHAIN) {
                  ListenableFuture<MapEntry> future =
                      submit(
                          () ->
                              createResolveConfiguration(
                                  target,
                                  toolchainLookupMap,
                                  headerRoots,
                                  compilerSettings,
                                  blazeProjectData,
                                  compilerInfoCache));
                  mapEntryFutures.add(future);
                }
              }

              ImmutableMap.Builder<TargetKey, BlazeResolveConfiguration> newResolveConfigurations =
                  ImmutableMap.builder();
              List<MapEntry> mapEntries;
              try {
                mapEntries = Futures.allAsList(mapEntryFutures).get();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                context.setCancelled();
                return ImmutableMap.of();
              } catch (ExecutionException e) {
                IssueOutput.error("Could not build C resolve configurations: " + e).submit(context);
                LOG.error("Could not build C resolve configurations", e);
                return ImmutableMap.of();
              }

              for (MapEntry mapEntry : mapEntries) {
                // Skip over labels that don't have C configuration data.
                if (mapEntry != null) {
                  newResolveConfigurations.put(mapEntry.targetKey, mapEntry.configuration);
                }
              }
              return newResolveConfigurations.build();
            });
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  @Nullable
  private MapEntry createResolveConfiguration(
      TargetIdeInfo target,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<File, VirtualFile> headerRoots,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettingsMap,
      BlazeProjectData blazeProjectData,
      CompilerInfoCache compilerInfoCache) {
    TargetKey targetKey = target.key;
    CToolchainIdeInfo toolchainIdeInfo = toolchainLookupMap.get(targetKey);
    if (toolchainIdeInfo == null) {
      return null;
    }
    BlazeCompilerSettings compilerSettings = compilerSettingsMap.get(toolchainIdeInfo);
    if (compilerSettings == null) {
      return null;
    }
    BlazeResolveConfiguration config =
        BlazeResolveConfiguration.createConfigurationForTarget(
            project,
            new ExecutionRootPathResolver(
                Blaze.getBuildSystem(project),
                WorkspaceRoot.fromProject(project),
                blazeProjectData.blazeInfo.getExecutionRoot(),
                blazeProjectData.workspacePathResolver),
            blazeProjectData.workspacePathResolver,
            headerRoots,
            blazeProjectData.targetMap.get(targetKey),
            toolchainIdeInfo,
            compilerSettings,
            compilerInfoCache);
    if (config == null) {
      return null;
    }
    return new MapEntry(targetKey, config);
  }

  private static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> buildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      WorkspacePathResolver workspacePathResolver) {
    Set<CToolchainIdeInfo> toolchains =
        toolchainLookupMap.values().stream().distinct().collect(Collectors.toSet());
    List<ListenableFuture<Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings>>>
        compilerSettingsFutures = new ArrayList<>();
    for (CToolchainIdeInfo toolchain : toolchains) {
      compilerSettingsFutures.add(
          submit(
              () -> {
                BlazeCompilerSettings settings =
                    createBlazeCompilerSettings(project, toolchain, workspacePathResolver);
                if (settings == null) {
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
      LOG.error("Could not build C compiler settings map", e);
    }
    return compilerSettingsMap.build();
  }

  @Nullable
  private static BlazeCompilerSettings createBlazeCompilerSettings(
      Project project,
      CToolchainIdeInfo toolchainIdeInfo,
      WorkspacePathResolver workspacePathResolver) {
    File compilerWrapper = getCompilerWrapper(toolchainIdeInfo, workspacePathResolver);
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
        project, compilerWrapper, compilerWrapper, cFlagsBuilder.build(), cppFlagsBuilder.build());
  }

  @Nullable
  private static File getCompilerWrapper(
      CToolchainIdeInfo toolchainIdeInfo, WorkspacePathResolver workspacePathResolver) {
    File cppExecutable = toolchainIdeInfo.cppExecutable.getAbsoluteOrRelativeFile();
    if (cppExecutable != null && !cppExecutable.isAbsolute()) {
      cppExecutable = workspacePathResolver.resolveToFile(cppExecutable.getPath());
    }
    if (cppExecutable == null) {
      LOG.warn(
          String.format(
              "Unable to find compiler executable: %s for toolchain %s",
              toolchainIdeInfo.cppExecutable.toString(), toolchainIdeInfo));
      return null;
    }
    return createCompilerExecutableWrapper(cppExecutable);
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

      try (PrintWriter pw = new PrintWriter(blazeCompilerWrapper, UTF_8.name())) {
        compilerWrapperScriptLines.forEach(pw::println);
      }
      return blazeCompilerWrapper;
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  public OCResolveConfiguration getConfigurationForFile(VirtualFile sourceFile) {
    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    ImmutableCollection<TargetKey> targetsForSourceFile =
        sourceToTargetMap.getRulesForSourceFile(VfsUtilCore.virtualToIoFile(sourceFile));
    if (targetsForSourceFile.isEmpty()) {
      return null;
    }

    // If a source file is in two different targets, we can't possibly show how it will be
    // interpreted in both contexts at the same time in the IDE, so just pick the "first" target.
    TargetKey targetKey = targetsForSourceFile.stream().min(TargetKey::compareTo).orElse(null);
    assert (targetKey != null);

    return resolveConfigurations.get(targetKey);
  }

  ImmutableList<? extends OCResolveConfiguration> getAllConfigurations() {
    return resolveConfigurations.values().asList();
  }
}
