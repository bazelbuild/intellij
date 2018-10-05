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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Blaze implementation of {@link OCResolveConfiguration}.
 *
 * <p>TODO(jvoung): BlazeResolveConfiguration are not used as a real OCResolveConfiguration so we
 * could simplify the interface and setup.
 */
final class BlazeResolveConfiguration {

  private final Project project;
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

  public String getDisplayName(boolean shorten) {
    return displayNameIdentifier;
  }

  public int compareTo(BlazeResolveConfiguration other) {
    // This is a bit of a weak comparison -- it just uses the display name (ignoring case)
    // and doesn't compare the actual fields of the configuration.
    // It should only be used for simple things like sorting for the UI.
    return getDisplayName(false).compareToIgnoreCase(other.getDisplayName(false));
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
  OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
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

  private OCLanguageKind getMaximumLanguageKind() {
    return OCLanguageKind.CPP;
  }

  // TODO(jvoung): simplify the tests so that we don't need these Internal methods.
  @VisibleForTesting
  List<HeadersSearchRoot> getProjectHeadersRootsInternal() {
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

  @VisibleForTesting
  List<HeadersSearchRoot> getLibraryHeadersRootsInternal(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile) {
    if (languageKind == null) {
      languageKind = getLanguageKind(sourceFile);
    }
    ImmutableSet.Builder<HeadersSearchRoot> roots = ImmutableSet.builder();
    if (languageKind == OCLanguageKind.C) {
      roots.addAll(configurationData.cLibraryIncludeRoots);
    } else {
      roots.addAll(configurationData.cppLibraryIncludeRoots);
    }
    return roots.build().asList();
  }

  BlazeCompilerSettings getCompilerSettings() {
    return configurationData.compilerSettings;
  }

  Collection<VirtualFile> getSources(BlazeProjectData blazeProjectData, TargetKey targetKey) {
    ImmutableList.Builder<VirtualFile> builder = ImmutableList.builder();

    TargetIdeInfo targetIdeInfo = blazeProjectData.getTargetMap().get(targetKey);
    if (targetIdeInfo.getcIdeInfo() == null) {
      return ImmutableList.of();
    }

    for (ArtifactLocation sourceArtifact : targetIdeInfo.getSources()) {
      File file = blazeProjectData.getArtifactLocationDecoder().decode(sourceArtifact);
      VirtualFile vf = VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(file);
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

  @VisibleForTesting
  ImmutableCollection<String> getDefinesInternal() {
    return configurationData.defines;
  }
}
