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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** A stub {@link OCWorkspace} to use for testing. */
class StubOCWorkspace implements OCWorkspace {

  private final List<OCResolveConfiguration> resolveConfigurations;

  StubOCWorkspace(Project project) {
    resolveConfigurations = new ArrayList<>();
    resolveConfigurations.add(new StubOCResolveConfiguration(project));
  }

  @Override
  public Collection<VirtualFile> getLibraryFilesToBuildSymbols() {
    return ImmutableList.of();
  }

  public boolean areFromSameProject(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return Objects.equals(a, b);
  }

  public boolean areFromSamePackage(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return Objects.equals(a, b);
  }

  public boolean isInSDK(@Nullable VirtualFile file) {
    return false;
  }

  public boolean isFromWrongSDK(OCSymbol symbol, @Nullable VirtualFile contextFile) {
    return false;
  }

  @Override
  public List<? extends OCResolveConfiguration> getConfigurations() {
    return resolveConfigurations;
  }

  @Override
  public List<? extends OCResolveConfiguration> getConfigurationsForFile(
      @Nullable VirtualFile sourceFile) {
    if (sourceFile == null) {
      return Collections.emptyList();
    }
    return OCFileType.INSTANCE.equals(sourceFile.getFileType())
        ? resolveConfigurations
        : Collections.emptyList();
  }
}
