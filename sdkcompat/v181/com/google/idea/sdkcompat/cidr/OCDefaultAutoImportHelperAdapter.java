/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.autoImport.OCDefaultAutoImportHelper;
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.List;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCDefaultAutoImportHelperAdapter extends OCDefaultAutoImportHelper {

  public abstract boolean supports(Project project);

  public boolean supports(OCResolveRootAndConfiguration rootAndConfiguration) {
    if (rootAndConfiguration.getConfiguration() == null) {
      return false;
    }
    return supports(rootAndConfiguration.getConfiguration().getProject());
  }

  public abstract boolean processPathSpecificationToInclude(
      Project project,
      @Nullable VirtualFile targetFile,
      final VirtualFile fileToImport,
      OCResolveRootAndConfigurationAdapter rootAndConfig,
      Processor<ImportSpecification> processor);

  public boolean processPathSpecificationToInclude(
      Project project,
      @Nullable VirtualFile targetFile,
      final VirtualFile fileToImport,
      OCResolveRootAndConfiguration rootAndConfiguration,
      Processor<ImportSpecification> processor) {
    OCResolveRootAndConfigurationAdapter adapter =
        new OCResolveRootAndConfigurationAdapter(
            rootAndConfiguration.getConfiguration(),
            rootAndConfiguration.getKind(),
            rootAndConfiguration.getRootFile());
    return processPathSpecificationToInclude(project, targetFile, fileToImport, adapter, processor);
  }

  /** Return roots that could be used for angle-bracket includes */
  protected List<HeadersSearchRoot> getSystemHeaderRoots(
      OCResolveRootAndConfigurationAdapter rootAndConfig) {
    if (rootAndConfig.getConfiguration() == null) {
      return ImmutableList.of();
    }
    return rootAndConfig
        .getConfiguration()
        .getLibraryHeadersRoots(rootAndConfig.getKind(), rootAndConfig.getRootFile());
  }

  /** Return roots that could be used for quote includes */
  protected List<HeadersSearchRoot> getUserHeaderRoots(
      OCResolveRootAndConfigurationAdapter rootAndConfig) {
    // In 2018.1, the getProjectHeaderRoots is just an empty list, and quote includes are
    // mixed into getLibraryHeadersRoots().
    if (rootAndConfig.getConfiguration() == null) {
      return ImmutableList.of();
    }
    return rootAndConfig
        .getConfiguration()
        .getLibraryHeadersRoots(rootAndConfig.getKind(), rootAndConfig.getRootFile());
  }
}
