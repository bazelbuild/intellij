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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.autoImport.OCDefaultAutoImportHelper;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import javax.annotation.Nullable;

/**
 * CLion's auto-import suggestions result in include paths relative to the current file (CPP-7593).
 * Instead, we want paths relative to the header search root (e.g. the relevant blaze/bazel package
 * path). Presumably this will be fixed in a future CLwB release, but in the meantime, fix it
 * ourselves.
 */
public class BlazeCppAutoImportHelper extends OCDefaultAutoImportHelper {

  @Override
  public boolean supports(OCResolveRootAndConfiguration rootAndConfiguration) {
    return rootAndConfiguration.getConfiguration()
        instanceof com.google.idea.blaze.cpp.BlazeResolveConfiguration;
  }

  /**
   * Search in project header roots only. All other cases are covered by CLion's default
   * implementation.
   */
  @Override
  public boolean processPathSpecificationToInclude(
      Project project,
      @Nullable VirtualFile targetFile,
      final VirtualFile fileToImport,
      OCResolveRootAndConfiguration rootAndConfiguration,
      Processor<ImportSpecification> processor) {
    String name = fileToImport.getName();
    String path = fileToImport.getPath();

    VirtualFile targetFileParent = targetFile != null ? targetFile.getParent() : null;

    if (targetFileParent != null && targetFileParent.equals(fileToImport.getParent())) {
      if (!processor.process(
          new ImportSpecification(name, ImportSpecification.Kind.PROJECT_HEADER))) {
        return false;
      }
    }

    for (PsiFileSystemItem root : rootAndConfiguration.getProjectHeadersRoots().getRoots()) {
      if (!(root instanceof IncludedHeadersRoot)) {
        continue;
      }
      VirtualFile rootBase = root.getVirtualFile();
      String relativePath = VfsUtilCore.getRelativePath(fileToImport, rootBase);
      if (relativePath == null) {
        continue;
      }
      if (!processor.process(
          new ImportSpecification(relativePath, ImportSpecification.Kind.PROJECT_HEADER))) {
        return false;
      }
    }
    return true;
  }
}
