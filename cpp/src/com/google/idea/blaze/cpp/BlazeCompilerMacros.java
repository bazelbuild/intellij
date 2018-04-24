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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.idea.sdkcompat.cidr.CompilerInfoCacheAdapter;
import com.google.idea.sdkcompat.cidr.OCCompilerMacrosAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;

final class BlazeCompilerMacros extends OCCompilerMacrosAdapter {
  private final Project project;

  private final CompilerInfoCacheAdapter compilerInfoCache;
  private final OCCompilerSettings compilerSettings;

  private final ImmutableCollection<String> globalDefines;
  private final ImmutableMap<String, String> globalFeatures;

  BlazeCompilerMacros(
      Project project,
      CompilerInfoCacheAdapter compilerInfoCache,
      OCCompilerSettings compilerSettings,
      ImmutableCollection<String> defines,
      ImmutableMap<String, String> features) {
    this.project = project;
    this.compilerInfoCache = compilerInfoCache;
    this.compilerSettings = compilerSettings;
    this.globalDefines = defines;
    this.globalFeatures = features;
  }


  @Override
  protected void fillFileMacros(OCInclusionContext context, PsiFile sourceFile) {
    fillFileMacrosInternal(
        project, compilerSettings, context, sourceFile, compilerInfoCache, globalFeatures);
  }

  @Override
  public String getAllDefines(OCLanguageKind kind, VirtualFile vf) {
    return getAllDefinesInternal(
        project, compilerSettings, compilerInfoCache, kind, vf, globalDefines);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BlazeCompilerMacros)) {
      return false;
    }
    BlazeCompilerMacros other = (BlazeCompilerMacros) obj;
    return this.globalDefines.equals(other.globalDefines)
        && this.globalFeatures.equals(other.globalFeatures);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(globalDefines, globalFeatures);
  }
}
