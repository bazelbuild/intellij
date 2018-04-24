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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.autoImport.OCDefaultAutoImportHelper;
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCDefaultAutoImportHelperAdapter extends OCDefaultAutoImportHelper {
  public abstract boolean supports(OCResolveRootAndConfigurationAdapter rootAndConfiguration);

  public boolean supports(OCResolveRootAndConfiguration rootAndConfiguration) {
    OCResolveRootAndConfigurationAdapter adapter =
        new OCResolveRootAndConfigurationAdapter(
            rootAndConfiguration.getConfiguration(),
            rootAndConfiguration.getKind(),
            rootAndConfiguration.getRootFile());
    return supports(adapter);
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
}
