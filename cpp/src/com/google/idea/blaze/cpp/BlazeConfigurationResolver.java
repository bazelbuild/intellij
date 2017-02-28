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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
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
        BlazeResolveConfiguration.buildToolchainLookupMap(
            context, blazeProjectData.targetMap, blazeProjectData.reverseDependencies);
    ImmutableMap<File, VirtualFile> headerRoots =
        collectHeaderRoots(context, blazeProjectData, toolchainLookupMap);
    resolveConfigurations =
        buildBlazeConfigurationMap(context, blazeProjectData, toolchainLookupMap, headerRoots);
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
            projectData.blazeRoots.executionRoot,
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
                  } else if (!projectData.blazeRoots.isOutputArtifact(path)
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

  private static Set<ExecutionRootPath> collectExecutionRootPaths(
      TargetMap targetMap, ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap) {
    Set<ExecutionRootPath> paths = Sets.newHashSet();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (target.cIdeInfo != null) {
        paths.addAll(target.cIdeInfo.transitiveSystemIncludeDirectories);
        paths.addAll(target.cIdeInfo.transitiveIncludeDirectories);
        paths.addAll(target.cIdeInfo.transitiveQuoteIncludeDirectories);
      }
    }
    for (CToolchainIdeInfo toolchain : toolchainLookupMap.values()) {
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
      ImmutableMap<File, VirtualFile> headerRoots) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableMap<TargetKey, BlazeResolveConfiguration>>)
            context -> {
              context.push(new TimingScope("Build C configuration map"));

              ConcurrentMap<CToolchainIdeInfo, File> compilerWrapperCache = Maps.newConcurrentMap();
              List<ListenableFuture<MapEntry>> mapEntryFutures = Lists.newArrayList();

              for (TargetIdeInfo target : blazeProjectData.targetMap.targets()) {
                if (target.kind.getLanguageClass() == LanguageClass.C) {
                  ListenableFuture<MapEntry> future =
                      submit(
                          () ->
                              createResolveConfiguration(
                                  target,
                                  toolchainLookupMap,
                                  headerRoots,
                                  compilerWrapperCache,
                                  blazeProjectData));
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
      ConcurrentMap<CToolchainIdeInfo, File> compilerWrapperCache,
      BlazeProjectData blazeProjectData) {
    TargetKey targetKey = target.key;

    CToolchainIdeInfo toolchainIdeInfo = toolchainLookupMap.get(targetKey);
    if (toolchainIdeInfo != null) {
      File compilerWrapper =
          findOrCreateCompilerWrapperScript(
              compilerWrapperCache,
              toolchainIdeInfo,
              blazeProjectData.workspacePathResolver,
              targetKey);
      if (compilerWrapper != null) {
        BlazeResolveConfiguration config =
            BlazeResolveConfiguration.createConfigurationForTarget(
                project,
                new ExecutionRootPathResolver(
                    Blaze.getBuildSystem(project),
                    WorkspaceRoot.fromProject(project),
                    blazeProjectData.blazeRoots.executionRoot,
                    blazeProjectData.workspacePathResolver),
                blazeProjectData.workspacePathResolver,
                headerRoots,
                blazeProjectData.targetMap.get(targetKey),
                toolchainIdeInfo,
                compilerWrapper);
        if (config != null) {
          return new MapEntry(targetKey, config);
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
      TargetKey targetKey) {
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
                toolchainIdeInfo.cppExecutable.toString(), targetKey);
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
    List<TargetKey> targetsForSourceFile =
        Lists.newArrayList(
            sourceToTargetMap.getRulesForSourceFile(VfsUtilCore.virtualToIoFile(sourceFile)));
    if (targetsForSourceFile.isEmpty()) {
      return null;
    }

    // If a source file is in two different targets,
    // we can't possibly show how it will be interpreted in both contexts at the same time
    // in the IDE, so just pick the first target after we sort.
    targetsForSourceFile.sort(Comparator.comparing(TargetKey::toString));
    TargetKey targetKey = Iterables.getFirst(targetsForSourceFile, null);
    assert (targetKey != null);

    return resolveConfigurations.get(targetKey);
  }

  public List<? extends OCResolveConfiguration> getAllConfigurations() {
    return ImmutableList.copyOf(resolveConfigurations.values());
  }
}
