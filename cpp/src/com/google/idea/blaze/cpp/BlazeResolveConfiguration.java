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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.sdkcompat.cidr.OCCompilerMacrosAdapter;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceUtil;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeaderRoots;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/** Blaze implementation of {@link OCResolveConfiguration}. */
final class BlazeResolveConfiguration extends OCResolveConfigurationAdapter {

  private final Project project;
  private final ConcurrentMap<Pair<OCLanguageKind, VirtualFile>, HeaderRoots>
      libraryIncludeRootsCache = new ConcurrentHashMap<>();
  private final WorkspacePathResolver workspacePathResolver;

  private final BlazeResolveConfigurationData configurationData;

  private String displayNameIdentifier;

  private BlazeResolveConfiguration(
      Project project,
      WorkspacePathResolver workspacePathResolver,
      BlazeResolveConfigurationData configurationData) {
    this.project = project;
    this.workspacePathResolver = workspacePathResolver;
    this.configurationData = configurationData;
  }

  static BlazeResolveConfiguration createForTargets(
      Project project,
      WorkspacePathResolver workspacePathResolver,
      BlazeResolveConfigurationData configurationData,
      Collection<TargetKey> targets) {
    BlazeResolveConfiguration result =
        new BlazeResolveConfiguration(project, workspacePathResolver, configurationData);
    result.representMultipleTargets(targets);
    return result;
  }

  /**
   * Indicate that this single configuration represents N other targets. NOTE: this changes the
   * identifier used by {@link #compareTo}, so any data structures using compareTo must be
   * invalidated when this changes.
   */
  void representMultipleTargets(Collection<TargetKey> targets) {
    TargetKey minTargetKey = targets.stream().min(TargetKey::compareTo).orElse(null);
    Preconditions.checkNotNull(minTargetKey);
    String minTarget = minTargetKey.toString();
    if (targets.size() == 1) {
      displayNameIdentifier = minTarget;
    } else {
      displayNameIdentifier =
          String.format("%s and %d other target(s)", minTarget, targets.size() - 1);
    }
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
    return displayNameIdentifier;
  }

  @Override
  public int compareTo(OCResolveConfiguration other) {
    // This is a bit of a weak comparison -- it just uses the display name (ignoring case)
    // and doesn't compare the actual fields of the configuration.
    // It should only be used for simple things like sorting for the UI.
    return OCWorkspaceUtil.compareConfigurations(this, other);
  }

  @Override
  public int hashCode() {
    // There should only be one configuration per target, and the display name is derived
    // from a target
    return Objects.hashCode(displayNameIdentifier);
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
    return configurationData.projectIncludeRoots;
  }

  @Override
  public HeaderRoots getLibraryHeadersRoots(OCResolveRootAndConfiguration headerContext) {
    OCLanguageKind languageKind = headerContext.getKind();
    VirtualFile sourceFile = headerContext.getRootFile();
    if (languageKind == null) {
      languageKind = getLanguageKind(sourceFile);
    }
    Pair<OCLanguageKind, VirtualFile> cacheKey = Pair.create(languageKind, sourceFile);
    return libraryIncludeRootsCache.computeIfAbsent(
        cacheKey,
        key -> {
          OCLanguageKind lang = key.first;
          VirtualFile source = key.second;
          ImmutableSet.Builder<HeadersSearchRoot> roots = ImmutableSet.builder();
          if (lang == OCLanguageKind.C) {
            roots.addAll(configurationData.cLibraryIncludeRoots);
          } else {
            roots.addAll(configurationData.cppLibraryIncludeRoots);
          }

          CidrCompilerResult<CompilerInfoCache.Entry> compilerInfoCacheHolder =
              configurationData.compilerSettings.getCompilerInfo(lang, source);
          CompilerInfoCache.Entry compilerInfo = compilerInfoCacheHolder.getResult();
          if (compilerInfo != null) {
            roots.addAll(compilerInfo.headerSearchPaths);
          }
          return new HeaderRoots(roots.build().asList());
        });
  }

  @Override
  public OCCompilerMacrosAdapter getCompilerMacros() {
    return configurationData.compilerMacros;
  }

  @Override
  public OCCompilerSettings getCompilerSettings() {
    return configurationData.compilerSettings;
  }

  @Override
  public Object getIndexingCluster() {
    return configurationData.toolchainIdeInfo;
  }

  /* #api163 */
  @Nullable
  @Override
  public VirtualFile getPrecompiledHeader() {
    return null;
  }

  /* #api163 */
  @Override
  public OCLanguageKind getPrecompiledLanguageKind() {
    return getMaximumLanguageKind();
  }

  /* #api171 */
  @Override
  public Set<VirtualFile> getPrecompiledHeaders() {
    return ImmutableSet.of();
  }

  /* #api171 */
  @Override
  public List<VirtualFile> getPrecompiledHeaders(OCLanguageKind kind, VirtualFile sourceFile) {
    return ImmutableList.of();
  }

  /* #api171 */
  @Override
  public Collection<VirtualFile> getSources() {
    return ImmutableList.of();
  }

  /* #api172 */
  @Override
  public String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile) {
    return configurationData.compilerMacros.getAllDefines(kind, virtualFile);
  }
}
