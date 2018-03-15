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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.sdkcompat.cidr.OCCompilerMacrosAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.util.Map;

final class BlazeCompilerMacros extends OCCompilerMacrosAdapter {
  private final Project project;

  private final CompilerInfoCache compilerInfoCache;
  private final OCCompilerSettings compilerSettings;

  private final ImmutableCollection<String> globalDefines;
  private final ImmutableMap<String, String> globalFeatures;

  BlazeCompilerMacros(
      Project project,
      CompilerInfoCache compilerInfoCache,
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
  public String getAllDefines(OCLanguageKind kind, VirtualFile vf) {
    CidrCompilerResult<CompilerInfoCache.Entry> compilerInfoProvider =
        compilerInfoCache.getCompilerInfoCache(project, compilerSettings, kind, vf);
    CompilerInfoCache.Entry compilerInfo = compilerInfoProvider.getResult();
    // Combine the info we got from Blaze with the info we get from IntelliJ's methods.
    ImmutableSet.Builder<String> allDefinesBuilder = ImmutableSet.builder();
    // IntelliJ expects a string of "#define [VAR_NAME] [VALUE]\n#define [VAR_NAME2] [VALUE]\n...",
    // where VALUE is optional.
    for (String globalDefine : globalDefines) {
      String[] split = globalDefine.split("=", 2);
      if (split.length == 1) {
        allDefinesBuilder.add("#define " + split[0]);
      } else {
        allDefinesBuilder.add("#define " + split[0] + " " + split[1]);
      }
    }
    String allDefines = String.join("\n", allDefinesBuilder.build());
    if (compilerInfo != null) {
      allDefines += "\n" + compilerInfo.defines;
    }

    return allDefines;
  }

  @Override
  protected void fillFileMacros(OCInclusionContext context, PsiFile sourceFile) {
    // Get the default compiler info for this file.
    VirtualFile vf = OCInclusionContextUtil.getVirtualFile(sourceFile);

    CidrCompilerResult<CompilerInfoCache.Entry> compilerInfoProvider =
        compilerInfoCache.getCompilerInfoCache(
            project, compilerSettings, context.getLanguageKind(), vf);
    CompilerInfoCache.Entry compilerInfo = compilerInfoProvider.getResult();

    Map<String, String> allFeatures = Maps.newHashMap();
    allFeatures.putAll(globalFeatures);
    if (compilerInfo != null) {
      addAllFeatures(allFeatures, compilerInfo.features);
    }

    fillSubstitutions(context, getAllDefines(context.getLanguageKind(), vf));
    enableClangFeatures(context, allFeatures);
    enableClangExtensions(context, allFeatures);
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
