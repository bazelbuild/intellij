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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.util.Map;

final class BlazeCompilerMacros extends OCCompilerMacros {
  private final CompilerInfoCache compilerInfoCache;
  private final ImmutableCollection<String> globalDefines;
  private final ImmutableMap<String, String> globalFeatures;
  private final OCCompilerSettings compilerSettings;
  private final Project project;

  public BlazeCompilerMacros(
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
  protected void fillFileMacros(OCInclusionContext context, PsiFile sourceFile) {
    // Get the default compiler info for this file.
    VirtualFile vf = OCInclusionContextUtil.getVirtualFile(sourceFile);
    CidrCompilerResult<CompilerInfoCache.Entry> compilerInfoProvider =
        compilerInfoCache.getCompilerInfoCache(
            project, compilerSettings, context.getLanguageKind(), vf);
    CompilerInfoCache.Entry compilerInfo = compilerInfoProvider.getResult();

    // Combine the info we got from Blaze with the info we get from IntelliJ's methods.
    ImmutableSet.Builder<String> allDefinesBuilder = ImmutableSet.builder();
    // IntelliJ expects a string of "#define [VAR_NAME]\n#define [VAR_NAME2]\n..."
    for (String globalDefine : globalDefines) {
      allDefinesBuilder.add("#define " + globalDefine);
    }
    if (compilerInfo != null) {
      String[] split = compilerInfo.defines.split("\n");
      for (String s : split) {
        allDefinesBuilder.add(s);
      }
    }
    final String allDefines = Joiner.on("\n").join(allDefinesBuilder.build());

    Map<String, String> allFeatures = Maps.newHashMap();
    allFeatures.putAll(globalFeatures);
    if (compilerInfo != null) {
      allFeatures.putAll(compilerInfo.features);
    }

    fillSubstitutions(context, allDefines);
    enableClangFeatures(context, allFeatures);
    enableClangExtensions(context, allFeatures);
  }
}
