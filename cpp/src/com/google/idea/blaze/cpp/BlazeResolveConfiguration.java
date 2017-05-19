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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceUtil;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeaderRoots;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class BlazeResolveConfiguration extends UserDataHolderBase
    implements OCResolveConfigurationAdapter {

  private static final Logger logger = Logger.getInstance(BlazeResolveConfiguration.class);
  private final ExecutionRootPathResolver executionRootPathResolver;
  private final WorkspacePathResolver workspacePathResolver;

  /* project, label are protected instead of private just so v145 can access */
  protected final Project project;
  protected final TargetKey targetKey;

  private final ImmutableList<HeadersSearchRoot> cLibraryIncludeRoots;
  private final ImmutableList<HeadersSearchRoot> cppLibraryIncludeRoots;
  private final HeaderRoots projectIncludeRoots;
  private final ConcurrentMap<Pair<OCLanguageKind, VirtualFile>, HeaderRoots> libraryIncludeRoots =
      new ConcurrentHashMap<>();

  private final CompilerInfoCache compilerInfoCache;
  private final BlazeCompilerMacros compilerMacros;
  private final BlazeCompilerSettings compilerSettings;
  private final CToolchainIdeInfo toolchainIdeInfo;

  @Nullable
  public static BlazeResolveConfiguration createConfigurationForTarget(
      Project project,
      ExecutionRootPathResolver executionRootPathResolver,
      WorkspacePathResolver workspacePathResolver,
      ImmutableMap<File, VirtualFile> headerRoots,
      TargetIdeInfo target,
      CToolchainIdeInfo toolchainIdeInfo,
      BlazeCompilerSettings compilerSettings,
      CompilerInfoCache compilerInfoCache) {
    CIdeInfo cIdeInfo = target.cIdeInfo;
    if (cIdeInfo == null) {
      return null;
    }

    ImmutableSet.Builder<ExecutionRootPath> systemIncludesBuilder = ImmutableSet.builder();
    systemIncludesBuilder.addAll(cIdeInfo.transitiveSystemIncludeDirectories);
    systemIncludesBuilder.addAll(toolchainIdeInfo.builtInIncludeDirectories);
    systemIncludesBuilder.addAll(toolchainIdeInfo.unfilteredToolchainSystemIncludes);

    ImmutableSet.Builder<ExecutionRootPath> userIncludesBuilder = ImmutableSet.builder();
    userIncludesBuilder.addAll(cIdeInfo.transitiveIncludeDirectories);
    userIncludesBuilder.addAll(cIdeInfo.localIncludeDirectories);

    ImmutableSet.Builder<ExecutionRootPath> userQuoteIncludesBuilder = ImmutableSet.builder();
    userQuoteIncludesBuilder.addAll(cIdeInfo.transitiveQuoteIncludeDirectories);

    ImmutableList.Builder<String> defines = ImmutableList.builder();
    defines.addAll(cIdeInfo.transitiveDefines);
    defines.addAll(cIdeInfo.localDefines);

    ImmutableMap<String, String> features = ImmutableMap.of();

    return new BlazeResolveConfiguration(
        project,
        executionRootPathResolver,
        workspacePathResolver,
        headerRoots,
        target.key,
        systemIncludesBuilder.build(),
        systemIncludesBuilder.build(),
        userQuoteIncludesBuilder.build(),
        userIncludesBuilder.build(),
        userIncludesBuilder.build(),
        defines.build(),
        features,
        compilerSettings,
        compilerInfoCache,
        toolchainIdeInfo);
  }

  static ImmutableMap<TargetKey, CToolchainIdeInfo> buildToolchainLookupMap(
      BlazeContext context, TargetMap targetMap) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Build toolchain lookup map"));

          Map<TargetKey, CToolchainIdeInfo> toolchains = Maps.newLinkedHashMap();
          for (TargetIdeInfo target : targetMap.targets()) {
            CToolchainIdeInfo cToolchainIdeInfo = target.cToolchainIdeInfo;
            if (cToolchainIdeInfo != null) {
              toolchains.put(target.key, cToolchainIdeInfo);
            }
          }

          ImmutableMap.Builder<TargetKey, CToolchainIdeInfo> lookupTable = ImmutableMap.builder();
          for (TargetIdeInfo target : targetMap.targets()) {
            if (target.kind.getLanguageClass() != LanguageClass.C
                || target.kind == Kind.CC_TOOLCHAIN) {
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

  public BlazeResolveConfiguration(
      Project project,
      ExecutionRootPathResolver executionRootPathResolver,
      WorkspacePathResolver workspacePathResolver,
      ImmutableMap<File, VirtualFile> headerRoots,
      TargetKey targetKey,
      ImmutableCollection<ExecutionRootPath> cSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> quoteIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppIncludeDirs,
      ImmutableCollection<String> defines,
      ImmutableMap<String, String> features,
      BlazeCompilerSettings compilerSettings,
      CompilerInfoCache compilerInfoCache,
      CToolchainIdeInfo toolchainIdeInfo) {
    this.executionRootPathResolver = executionRootPathResolver;
    this.workspacePathResolver = workspacePathResolver;
    this.project = project;
    this.targetKey = targetKey;
    this.toolchainIdeInfo = toolchainIdeInfo;

    ImmutableList.Builder<HeadersSearchRoot> cIncludeRootsBuilder = ImmutableList.builder();
    collectHeaderRoots(headerRoots, cIncludeRootsBuilder, cIncludeDirs, true /* isUserHeader */);
    collectHeaderRoots(
        headerRoots, cIncludeRootsBuilder, cSystemIncludeDirs, false /* isUserHeader */);
    this.cLibraryIncludeRoots = cIncludeRootsBuilder.build();

    ImmutableList.Builder<HeadersSearchRoot> cppIncludeRootsBuilder = ImmutableList.builder();
    collectHeaderRoots(
        headerRoots, cppIncludeRootsBuilder, cppIncludeDirs, true /* isUserHeader */);
    collectHeaderRoots(
        headerRoots, cppIncludeRootsBuilder, cppSystemIncludeDirs, false /* isUserHeader */);
    this.cppLibraryIncludeRoots = cppIncludeRootsBuilder.build();

    ImmutableList.Builder<HeadersSearchRoot> quoteIncludeRootsBuilder = ImmutableList.builder();
    collectHeaderRoots(
        headerRoots, quoteIncludeRootsBuilder, quoteIncludeDirs, true /* isUserHeader */);
    this.projectIncludeRoots = new HeaderRoots(quoteIncludeRootsBuilder.build());

    this.compilerSettings = compilerSettings;
    this.compilerInfoCache = compilerInfoCache;
    this.compilerMacros =
        new BlazeCompilerMacros(project, compilerInfoCache, compilerSettings, defines, features);
  }

  @Override
  public Project getProject() {
    return project;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  @Override
  public String getDisplayName(boolean shorten) {
    return targetKey.toString();
  }

  @Nullable
  @Override
  public OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile);
    }

    if (OCFileTypeHelpers.isHeaderFile(fileName)) {
      return getLanguageKind(getSourceFileForHeaderFile(sourceOrHeaderFile));
    }

    return null;
  }

  private OCLanguageKind getLanguageKind(@Nullable VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Nullable
  private VirtualFile getSourceFileForHeaderFile(VirtualFile headerFile) {
    Collection<VirtualFile> roots = OCImportGraph.getAllHeaderRoots(project, headerFile);

    final String headerNameWithoutExtension = headerFile.getNameWithoutExtension();
    for (VirtualFile root : roots) {
      if (root.getNameWithoutExtension().equals(headerNameWithoutExtension)) {
        return root;
      }
    }
    return null;
  }

  @Override
  public OCLanguageKind getMaximumLanguageKind() {
    return OCLanguageKind.CPP;
  }

  @Override
  public HeaderRoots getProjectHeadersRoots() {
    return projectIncludeRoots;
  }

  @Override
  public HeaderRoots getLibraryHeadersRoots(OCResolveRootAndConfiguration headerContext) {
    OCLanguageKind languageKind = headerContext.getKind();
    VirtualFile sourceFile = headerContext.getRootFile();
    if (languageKind == null) {
      languageKind = getLanguageKind(sourceFile);
    }
    Pair<OCLanguageKind, VirtualFile> cacheKey = Pair.create(languageKind, sourceFile);
    return libraryIncludeRoots.computeIfAbsent(
        cacheKey,
        key -> {
          OCLanguageKind lang = key.first;
          VirtualFile source = key.second;
          ImmutableSet.Builder<HeadersSearchRoot> roots = ImmutableSet.builder();
          if (lang == OCLanguageKind.C) {
            roots.addAll(cLibraryIncludeRoots);
          } else {
            roots.addAll(cppLibraryIncludeRoots);
          }

          CidrCompilerResult<CompilerInfoCache.Entry> compilerInfoCacheHolder =
              compilerInfoCache.getCompilerInfoCache(project, compilerSettings, lang, source);
          CompilerInfoCache.Entry compilerInfo = compilerInfoCacheHolder.getResult();
          if (compilerInfo != null) {
            roots.addAll(compilerInfo.headerSearchPaths);
          }
          return new HeaderRoots(roots.build().asList());
        });
  }

  private void collectHeaderRoots(
      ImmutableMap<File, VirtualFile> virtualFileCache,
      ImmutableList.Builder<HeadersSearchRoot> roots,
      ImmutableCollection<ExecutionRootPath> paths,
      boolean isUserHeader) {
    for (ExecutionRootPath executionRootPath : paths) {
      ImmutableList<File> possibleDirectories =
          executionRootPathResolver.resolveToIncludeDirectories(executionRootPath);
      for (File f : possibleDirectories) {
        VirtualFile vf = virtualFileCache.get(f);
        if (vf != null) {
          roots.add(new IncludedHeadersRoot(project, vf, false /* recursive */, isUserHeader));
        }
      }
    }
  }

  @Override
  public OCCompilerMacros getCompilerMacros() {
    return compilerMacros;
  }

  @Override
  public OCCompilerSettings getCompilerSettings() {
    return compilerSettings;
  }

  @Override
  public Object getIndexingCluster() {
    return toolchainIdeInfo;
  }

  @Override
  public int compareTo(OCResolveConfiguration other) {
    return OCWorkspaceUtil.compareConfigurations(this, other);
  }

  @Override
  public int hashCode() {
    // There should only be one configuration per target.
    return Objects.hash(targetKey);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BlazeResolveConfiguration)) {
      return false;
    }

    BlazeResolveConfiguration that = (BlazeResolveConfiguration) obj;
    return compareTo(that) == 0;
  }

  /* This function is part of the v162/v163 plugin APIs. */
  @Nullable
  @Override
  public VirtualFile getPrecompiledHeader() {
    return null;
  }

  /* This function is part of the v162/v163 plugin APIs. */
  @Override
  public OCLanguageKind getPrecompiledLanguageKind() {
    return getMaximumLanguageKind();
  }

  /* This function is part of the v171 plugin API. */
  @Override
  public Set<VirtualFile> getPrecompiledHeaders() {
    return ImmutableSet.of();
  }

  /* This function is part of the v171 plugin API. */
  @Override
  public List<VirtualFile> getPrecompiledHeaders(OCLanguageKind kind, VirtualFile sourceFile) {
    return ImmutableList.of();
  }

  /* This function is part of the v171 plugin API. */
  @Override
  public Collection<VirtualFile> getSources() {
    return ImmutableList.of();
  }
}
