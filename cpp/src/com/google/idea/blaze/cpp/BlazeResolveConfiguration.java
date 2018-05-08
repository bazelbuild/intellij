/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.sdkcompat.cidr.OCCompilerMacrosAdapter;
import com.google.idea.sdkcompat.cidr.OCCompilerSettingsAdapter;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceUtil;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Blaze implementation of {@link OCResolveConfiguration}. */
final class BlazeResolveConfiguration extends OCResolveConfigurationAdapter {

  private final Project project;
  private final ConcurrentMap<Pair<OCLanguageKind, VirtualFile>, List<HeadersSearchRoot>>
      libraryIncludeRootsCache = new ConcurrentHashMap<>();

  private final BlazeResolveConfigurationData configurationData;

  private String displayNameIdentifier;

  private Collection<TargetKey> targets;

  private BlazeResolveConfiguration(
      Project project,
      BlazeResolveConfigurationData configurationData,
      Collection<TargetKey> targets) {
    this.project = project;
    this.configurationData = configurationData;
    representMultipleTargets(targets);
  }

  static BlazeResolveConfiguration createForTargets(
      Project project,
      BlazeResolveConfigurationData configurationData,
      Collection<TargetKey> targets) {
    return new BlazeResolveConfiguration(project, configurationData, targets);
  }

  public Collection<TargetKey> getTargets() {
    return targets;
  }

  /**
   * Indicate that this single configuration represents N other targets. NOTE: this changes the
   * identifier used by {@link #compareTo}, so any data structures using compareTo must be
   * invalidated when this changes.
   */
  void representMultipleTargets(Collection<TargetKey> targets) {
    TargetKey minTargetKey = targets.stream().min(TargetKey::compareTo).orElse(null);
    Preconditions.checkNotNull(minTargetKey);
    this.targets = targets;
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
  public List<HeadersSearchRoot> getProjectHeadersRootsInternal() {
    // OCFileReferenceHelper checks if the virtual files in getLibraryHeadersRoots() are valid
    // before passing them along, but it does not check if getProjectHeadersRoots()
    // are valid first. Check https://youtrack.jetbrains.com/issue/CPP-11126 to see if upstream
    // code will start filtering at a higher level.
    List<HeadersSearchRoot> roots = configurationData.projectIncludeRoots;
    if (roots.stream().anyMatch(root -> !root.isValid())) {
      return roots.stream().filter(HeadersSearchRoot::isValid).collect(Collectors.toList());
    }
    return configurationData.projectIncludeRoots;
  }

  @Override
  public List<HeadersSearchRoot> getLibraryHeadersRootsInternal(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile) {
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
          roots.addAll(
              getHeadersSearchRootFromCompilerInfo(
                  configurationData.compilerSettings, lang, source));
          return roots.build().asList();
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
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<VirtualFile> builder = ImmutableList.builder();

    for (TargetKey target : targets) {
      builder.addAll(getSources(blazeProjectData, target));
    }
    return builder.build();
  }

  public Collection<VirtualFile> getSources(
      BlazeProjectData blazeProjectData, TargetKey targetKey) {
    ImmutableList.Builder<VirtualFile> builder = ImmutableList.builder();

    TargetIdeInfo targetIdeInfo = blazeProjectData.targetMap.get(targetKey);
    if (targetIdeInfo.cIdeInfo == null) {
      return ImmutableList.of();
    }

    for (ArtifactLocation sourceArtifact : targetIdeInfo.sources) {
      VirtualFile vf =
          VfsUtil.findFileByIoFile(
              blazeProjectData.artifactLocationDecoder.decode(sourceArtifact), false);
      if (vf == null) {
        continue;
      }
      if (!OCFileTypeHelpers.isSourceFile(vf.getName())) {
        continue;
      }
      builder.add(vf);
    }
    return builder.build();
  }

  /* #api172 */
  @Override
  public String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile) {
    return configurationData.compilerMacros.getAllDefines(kind, virtualFile);
  }

  /* #api181 */
  @Override
  public OCCompilerSettingsAdapter getCompilerSettingsAdapter() {
    return configurationData.compilerSettings;
  }
}
