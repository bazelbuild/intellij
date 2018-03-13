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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class BlazeConfigurationResolver {
  private static final Logger logger = Logger.getInstance(BlazeConfigurationResolver.class);
  // Don't recursively check too many directories, in case the root is just too big.
  // Sometimes genfiles/java is considered a header search root.
  private static final int GEN_HEADER_ROOT_SEARCH_LIMIT = 50;

  private final Project project;

  BlazeConfigurationResolver(Project project) {
    this.project = project;
  }

  public BlazeConfigurationResolverResult update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      BlazeConfigurationResolverResult oldResult) {
    ExecutionRootPathResolver executionRootPathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystem(project),
            WorkspaceRoot.fromProject(project),
            blazeProjectData.blazeInfo.getExecutionRoot(),
            blazeProjectData.workspacePathResolver);
    ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap =
        BlazeConfigurationToolchainResolver.buildToolchainLookupMap(
            context, blazeProjectData.targetMap);
    ImmutableMap<File, VirtualFile> headerRoots =
        collectHeaderRoots(
            context, blazeProjectData, toolchainLookupMap, executionRootPathResolver);
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings =
        BlazeConfigurationToolchainResolver.buildCompilerSettingsMap(
            context,
            project,
            toolchainLookupMap,
            executionRootPathResolver,
            compilerInfoCache,
            oldResult.compilerSettings);
    BlazeConfigurationResolverResult.Builder builder =
        BlazeConfigurationResolverResult.builder(project);
    buildBlazeConfigurationData(
        context,
        workspaceRoot,
        projectViewSet,
        blazeProjectData,
        toolchainLookupMap,
        headerRoots,
        compilerSettings,
        compilerInfoCache,
        executionRootPathResolver,
        oldResult,
        builder);
    builder.setCompilerSettings(compilerSettings);
    return builder.build();
  }

  private static ImmutableMap<File, VirtualFile> collectHeaderRoots(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver executionRootPathResolver) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableMap<File, VirtualFile>>)
            context -> {
              context.push(new TimingScope("Resolve header include roots", EventType.Other));
              Set<ExecutionRootPath> paths =
                  collectExecutionRootPaths(blazeProjectData.targetMap, toolchainLookupMap);
              return doCollectHeaderRoots(
                  context, blazeProjectData, paths, executionRootPathResolver);
            });
  }

  private static ImmutableMap<File, VirtualFile> doCollectHeaderRoots(
      BlazeContext context,
      BlazeProjectData projectData,
      Set<ExecutionRootPath> rootPaths,
      ExecutionRootPathResolver pathResolver) {
    ConcurrentMap<File, VirtualFile> rootsMap = Maps.newConcurrentMap();
    List<ListenableFuture<Void>> futures = Lists.newArrayListWithCapacity(rootPaths.size());
    AtomicInteger genRootsWithHeaders = new AtomicInteger();
    AtomicInteger genRootsWithoutHeaders = new AtomicInteger();
    for (ExecutionRootPath path : rootPaths) {
      futures.add(
          submit(
              () -> {
                ImmutableList<File> possibleDirectories =
                    pathResolver.resolveToIncludeDirectories(path);
                if (possibleDirectories.isEmpty()) {
                  logger.info(String.format("Couldn't resolve include root: %s", path));
                }
                for (File file : possibleDirectories) {
                  VirtualFile vf = VfsUtils.resolveVirtualFile(file);
                  if (vf != null) {
                    // Check gen directories to see if they actually contain headers and not just
                    // other random generated files (like .s, .cc, or module maps).
                    if (!isOutputArtifact(projectData.blazeInfo, path)) {
                      rootsMap.put(file, vf);
                    } else if (genRootMayContainHeaders(vf)) {
                      genRootsWithHeaders.incrementAndGet();
                      rootsMap.put(file, vf);
                    } else {
                      genRootsWithoutHeaders.incrementAndGet();
                    }
                  } else if (!isOutputArtifact(projectData.blazeInfo, path)
                      && FileOperationProvider.getInstance().exists(file)) {
                    // If it's not a blaze output file, we expect it to always resolve.
                    logger.info(String.format("Unresolved header root %s", file.getAbsolutePath()));
                  }
                }
                return null;
              }));
    }
    try {
      Futures.allAsList(futures).get();
      ImmutableMap<File, VirtualFile> result = ImmutableMap.copyOf(rootsMap);
      logger.info(
          String.format(
              "CollectHeaderRoots: %s roots, (%s, %s) genroots with/without headers",
              result.size(), genRootsWithHeaders.get(), genRootsWithoutHeaders.get()));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error("Error resolving header include roots: " + e).submit(context);
      logger.error("Error resolving header include roots", e);
    }
    return ImmutableMap.of();
  }

  private static boolean genRootMayContainHeaders(VirtualFile directory) {
    int totalDirectoriesChecked = 0;
    Queue<VirtualFile> worklist = new ArrayDeque<>();
    worklist.add(directory);
    while (!worklist.isEmpty()) {
      totalDirectoriesChecked++;
      if (totalDirectoriesChecked > GEN_HEADER_ROOT_SEARCH_LIMIT) {
        return true;
      }
      VirtualFile dir = worklist.poll();
      for (VirtualFile child : dir.getChildren()) {
        if (child.isDirectory()) {
          worklist.add(child);
          continue;
        }
        String fileExtension = child.getExtension();
        if (CFileExtensions.HEADER_EXTENSIONS.contains(fileExtension)) {
          return true;
        }
      }
    }
    return false;
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

  private static boolean containsCompiledSources(TargetIdeInfo target) {
    Predicate<ArtifactLocation> isCompiled =
        location -> {
          String locationExtension = FileUtilRt.getExtension(location.getRelativePath());
          return CFileExtensions.SOURCE_EXTENSIONS.contains(locationExtension);
        };
    return target.cIdeInfo != null
        && target.cIdeInfo.sources.stream().filter(ArtifactLocation::isSource).anyMatch(isCompiled);
  }

  private void buildBlazeConfigurationData(
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<File, VirtualFile> headerRoots,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings,
      CompilerInfoCache compilerInfoCache,
      ExecutionRootPathResolver executionRootPathResolver,
      BlazeConfigurationResolverResult oldConfigurationData,
      BlazeConfigurationResolverResult.Builder builder) {
    // Type specification needed to avoid incorrect type inference during command line build.
    Scope.push(
        parentContext,
        (ScopedOperation)
            context -> {
              context.push(new TimingScope("Build C configuration map", EventType.Other));

              ProjectViewTargetImportFilter filter =
                  new ProjectViewTargetImportFilter(project, workspaceRoot, projectViewSet);

              ConcurrentMap<TargetKey, BlazeResolveConfigurationData> targetToData =
                  Maps.newConcurrentMap();
              List<ListenableFuture<?>> targetToDataFutures =
                  blazeProjectData
                      .targetMap
                      .targets()
                      .stream()
                      .filter(target -> target.kind.languageClass == LanguageClass.C)
                      .filter(target -> target.kind != Kind.CC_TOOLCHAIN)
                      .filter(filter::isSourceTarget)
                      .filter(BlazeConfigurationResolver::containsCompiledSources)
                      .map(
                          target ->
                              submit(
                                  () -> {
                                    BlazeResolveConfigurationData data =
                                        createResolveConfiguration(
                                            target,
                                            toolchainLookupMap,
                                            headerRoots,
                                            compilerSettings,
                                            compilerInfoCache,
                                            executionRootPathResolver);
                                    if (data != null) {
                                      targetToData.put(target.key, data);
                                    }
                                    return null;
                                  }))
                      .collect(Collectors.toList());
              try {
                Futures.allAsList(targetToDataFutures).get();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                context.setCancelled();
                return;
              } catch (ExecutionException e) {
                IssueOutput.error("Could not build C resolve configurations: " + e).submit(context);
                logger.error("Could not build C resolve configurations", e);
                return;
              }
              findEquivalenceClasses(
                  context,
                  project,
                  blazeProjectData.workspacePathResolver,
                  targetToData,
                  oldConfigurationData,
                  builder);
            });
  }

  private static void findEquivalenceClasses(
      BlazeContext context,
      Project project,
      WorkspacePathResolver workspacePathResolver,
      Map<TargetKey, BlazeResolveConfigurationData> targetToData,
      BlazeConfigurationResolverResult oldConfigurationData,
      BlazeConfigurationResolverResult.Builder builder) {
    Map<BlazeResolveConfigurationData, BlazeResolveConfiguration> dataToConfiguration =
        new HashMap<>();
    Multimap<BlazeResolveConfigurationData, TargetKey> dataEquivalenceClasses =
        ArrayListMultimap.create();
    int reused = 0;
    for (Map.Entry<TargetKey, BlazeResolveConfigurationData> entry : targetToData.entrySet()) {
      TargetKey target = entry.getKey();
      BlazeResolveConfigurationData data = entry.getValue();
      if (!dataToConfiguration.containsKey(data)) {
        BlazeResolveConfiguration configuration;
        if (oldConfigurationData.uniqueResolveConfigurations.containsKey(data)) {
          configuration = oldConfigurationData.uniqueResolveConfigurations.get(data);
          reused++;
        } else {
          configuration =
              BlazeResolveConfiguration.createForTargets(
                  project, workspacePathResolver, data, ImmutableList.of(target));
        }
        dataToConfiguration.put(data, configuration);
      }
      dataEquivalenceClasses.put(data, target);
    }
    ImmutableMap.Builder<TargetKey, BlazeResolveConfiguration> targetToConfiguration =
        ImmutableMap.builder();
    for (Map.Entry<BlazeResolveConfigurationData, Collection<TargetKey>> entry :
        dataEquivalenceClasses.asMap().entrySet()) {
      BlazeResolveConfigurationData data = entry.getKey();
      Collection<TargetKey> targets = entry.getValue();
      BlazeResolveConfiguration configuration = dataToConfiguration.get(data);
      configuration.representMultipleTargets(targets);
      for (TargetKey targetKey : targets) {
        targetToConfiguration.put(targetKey, configuration);
      }
    }
    context.output(
        PrintOutput.log(
            String.format(
                "%s unique C configurations (%s reused), %s C targets",
                dataEquivalenceClasses.keySet().size(), reused, dataEquivalenceClasses.size())));
    builder.setConfigurationMap(targetToConfiguration.build());
    builder.setUniqueConfigurations(ImmutableMap.copyOf(dataToConfiguration));
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  @Nullable
  private BlazeResolveConfigurationData createResolveConfiguration(
      TargetIdeInfo target,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<File, VirtualFile> headerRoots,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettingsMap,
      CompilerInfoCache compilerInfoCache,
      ExecutionRootPathResolver executionRootPathResolver) {
    TargetKey targetKey = target.key;
    CIdeInfo cIdeInfo = target.cIdeInfo;
    if (cIdeInfo == null) {
      return null;
    }
    CToolchainIdeInfo toolchainIdeInfo = toolchainLookupMap.get(targetKey);
    if (toolchainIdeInfo == null) {
      return null;
    }
    BlazeCompilerSettings compilerSettings = compilerSettingsMap.get(toolchainIdeInfo);
    if (compilerSettings == null) {
      return null;
    }
    return BlazeResolveConfigurationData.create(
        project,
        executionRootPathResolver,
        headerRoots,
        cIdeInfo,
        toolchainIdeInfo,
        compilerSettings,
        compilerInfoCache);
  }
}
