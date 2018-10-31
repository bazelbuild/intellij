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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationCompat;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSettingsKey;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures.Type;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoots;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** #api182: Stub {@link OCResolveConfiguration} for testing. */
public class StubOCResolveConfiguration extends UserDataHolderBase
    implements OCResolveConfiguration {

  private final Project project;
  private List<HeadersSearchRoot> libraryIncludeRoots;
  private final OCCompilerSettings compilerSettings;

  StubOCResolveConfiguration(Project project) {
    this.project = project;
    this.libraryIncludeRoots = ImmutableList.of();
    this.compilerSettings = new StubOCCompilerSettings(project);
  }

  public void setLibraryIncludeRoots(List<HeadersSearchRoot> searchRoots) {
    this.libraryIncludeRoots = searchRoots;
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public String getDisplayName(boolean shorten) {
    return "Stub resolve configuration";
  }

  @Override
  public String getUniqueId() {
    return getDisplayName(false);
  }

  @Override
  public Map<Type<?>, ?> getCompilerFeatures(
      OCLanguageKind kind, @Nullable VirtualFile virtualFile) {
    // Don't run the compiler during testing for feature testing (or mock out the run).
    return Collections.emptyMap();
  }

  @Override
  @Nullable
  public CompilerSettingsKey getCachingKey(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile) {
    return null;
  }

  @Override
  public Set<VirtualFile> getPrecompiledHeaders() {
    return Collections.emptySet();
  }

  @Override
  public List<VirtualFile> getPrecompiledHeaders(OCLanguageKind kind, VirtualFile sourceFile) {
    return Collections.emptyList();
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
    return OCLanguageKind.CPP;
  }

  @Override
  public Set<OCLanguageKind> getEnabledLanguageKinds() {
    return ImmutableSet.of(OCLanguageKind.C, OCLanguageKind.CPP);
  }

  private OCLanguageKind getLanguageKind(VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Override
  public HeadersSearchRoots getHeadersRoots(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile) {
    return HeadersSearchRoots.create(libraryIncludeRoots);
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

  /* v172 */
  // Since v173 getPreprocessorDefines has NotNull annotation
  @Override
  public String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile) {
    return "";
  }

  @Override
  @Nullable
  public VirtualFile getMappedInclude(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile, String include) {
    return null;
  }

  @Override
  public char[] getFileSeparators() {
    return CidrToolEnvironment.UNIX_FILE_SEPARATORS;
  }
}
