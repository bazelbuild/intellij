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
package com.google.idea.sdkcompat.cidr;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCCompilerMacros;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfo;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures.Type;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCResolveConfigurationAdapter extends UserDataHolderBase
    implements OCResolveConfiguration {
  /* v171 */
  public abstract OCCompilerMacros getCompilerMacros();

  /* v181 */
  public abstract OCCompilerSettingsAdapter getCompilerSettingsAdapter();

  @Override
  public Map<Type<?>, ?> getCompilerFeatures(
      OCLanguageKind kind, @Nullable VirtualFile virtualFile) {
    OCCompilerSettingsAdapter compilerSettings = getCompilerSettingsAdapter();
    return compilerSettings.getInfoCacheEntry(kind, virtualFile).getFeatures();
  }

  public abstract List<HeadersSearchRoot> getProjectHeadersRootsInternal();

  @Override
  public List<HeadersSearchRoot> getProjectHeadersRoots() {
    return getProjectHeadersRootsInternal();
  }

  public abstract List<HeadersSearchRoot> getLibraryHeadersRootsInternal(
      OCLanguageKind kind, @Nullable VirtualFile virtualFile);

  @Override
  public List<HeadersSearchRoot> getLibraryHeadersRoots(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile) {
    return getLibraryHeadersRootsInternal(languageKind, sourceFile);
  }

  @Override
  public char[] getFileSeparators() {
    return CidrToolEnvironment.UNIX_FILE_SEPARATORS;
  }

  @Override
  public String getUniqueId() {
    return getDisplayName(false);
  }

  @Override
  @Nullable
  public VirtualFile getMappedInclude(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile, String include) {
    return null;
  }

  protected List<HeadersSearchRoot> getHeadersSearchRootFromCompilerInfo(
      OCCompilerSettingsAdapter compilerSettings, OCLanguageKind kind, VirtualFile virtualFile) {
    CompilerInfo info = compilerSettings.getInfoCacheEntry(kind, virtualFile);
    if (info == null) {
      return ImmutableList.of();
    }
    return info.getHeadersSearchPaths()
        .stream()
        .filter(Objects::nonNull)
        .map(path -> HeadersSearchRoot.createFromHeaderSearchPath(getProject(), path, getProject()))
        .collect(Collectors.toList());
  }
}
