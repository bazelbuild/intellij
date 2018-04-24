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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import com.jetbrains.cidr.toolchains.CompilerInfoCache.Entry;
import java.util.Map;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerMacrosAdapter extends OCCompilerMacros {
  // v171
  public void addAllFeatures(Map<String, String> result, Map<String, String> features) {
    result.putAll(features);
  }
  // v172
  public abstract String getAllDefines(OCLanguageKind kind, VirtualFile vf);

  // v173
  protected String getAllDefinesInternal(
      Project project,
      OCCompilerSettings compilerSettings,
      CompilerInfoCacheAdapter compilerInfoCache,
      OCLanguageKind kind,
      VirtualFile vf,
      ImmutableCollection<String> globalDefines) {
    CidrCompilerResult<Entry> compilerInfoProvider =
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

  protected void fillFileMacrosInternal(
      Project project,
      OCCompilerSettings compilerSettings,
      OCInclusionContext context,
      PsiFile sourceFile,
      CompilerInfoCacheAdapter compilerInfoCache,
      ImmutableMap<String, String> globalFeatures) {
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
}
