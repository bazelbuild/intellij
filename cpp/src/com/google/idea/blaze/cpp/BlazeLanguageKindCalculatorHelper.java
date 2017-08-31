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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculatorHelper;
import javax.annotation.Nullable;

final class BlazeLanguageKindCalculatorHelper implements OCLanguageKindCalculatorHelper {
  @Nullable
  @Override
  public OCLanguageKind getSpecifiedLanguage(Project project, VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public OCLanguageKind getLanguageByExtension(Project project, String name) {
    if (Blaze.isBlazeProject(project)) {
      String extension = FileUtilRt.getExtension(name);
      if (CFileExtensions.C_FILE_EXTENSIONS.contains(extension)) {
        return OCLanguageKind.C;
      }
      if (CFileExtensions.CXX_FILE_EXTENSIONS.contains(extension)) {
        return OCLanguageKind.CPP;
      }
      if (CFileExtensions.CXX_ONLY_HEADER_EXTENSIONS.contains(extension)) {
        return OCLanguageKind.CPP;
      }
    }
    return null;
  }
}
