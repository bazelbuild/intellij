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
package com.google.idea.testing.cidr;

import com.google.common.collect.ImmutableSet;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationCompat;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCVariant;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** #api182: Stub {@link OCResolveConfiguration} for testing. */
public class StubOCResolveConfiguration extends UserDataHolderBase
    implements OCResolveConfiguration {

  private final Project project;
  private final StubOCCompilerSettings compilerSettings;

  StubOCResolveConfiguration(Project project) {
    this.project = project;
    this.compilerSettings = new StubOCCompilerSettings(project);
  }

  public void setLibraryIncludeRoots(List<HeadersSearchRoot> searchRoots) {
    compilerSettings.setLibraryIncludeRoots(searchRoots);
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public String getUniqueId() {
    return getName();
  }

  @Override
  public String getName() {
    return "Stub Resolve Configuration";
  }

  @Nullable
  @Override
  public OCVariant getVariant() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return getName();
  }

  @Override
  public String getFileSeparators() {
    return CidrToolEnvironment.UNIX_FILE_SEPARATORS;
  }

  @Override
  public Collection<VirtualFile> getSources() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile);
    }
    return getMaximumLanguageKind();
  }

  private OCLanguageKind getMaximumLanguageKind() {
    return CLanguageKind.CPP;
  }

  @Override
  public Set<OCLanguageKind> getEnabledLanguageKinds() {
    return ImmutableSet.of(CLanguageKind.C, CLanguageKind.CPP);
  }

  @Override
  public Set<VirtualFile> getAllPrecompiledHeaders() {
    return ImmutableSet.of();
  }

  @Override
  public Stream<OCCompilerSettings> getAllCompilerSettings() {
    return Stream.of();
  }

  private OCLanguageKind getLanguageKind(VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Override
  public OCCompilerSettings getCompilerSettings(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile) {
    return compilerSettings;
  }

  @Nullable
  @Override
  public Object getIndexingCluster() {
    return null;
  }

  @Override
  public int compareTo(OCResolveConfiguration o) {
    return OCResolveConfigurationCompat.compareConfigurations(this, o);
  }
}
