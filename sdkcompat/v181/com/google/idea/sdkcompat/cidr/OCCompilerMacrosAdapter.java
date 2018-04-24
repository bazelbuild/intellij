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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCCompilerMacros;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.util.Map;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerMacrosAdapter extends OCCompilerMacros {

  // v171
  protected abstract void fillFileMacros(OCInclusionContext context, PsiFile sourceFile);

  // v171
  protected void addAllFeatures(
      Map<String, String> collection, Map<OCCompilerFeatures.Type<?>, ?> features) {}

  // v171
  public static void fillSubstitutions(OCInclusionContext context, String text) {}

  // v171
  public void enableClangFeatures(OCInclusionContext context, Map<String, String> features) {}

  // v171
  public void enableClangExtensions(OCInclusionContext context, Map<String, String> extensions) {}

  // v172
  public abstract String getAllDefines(OCLanguageKind kind, VirtualFile vf);

  protected String getAllDefinesInternal(
      Project project,
      OCCompilerSettings compilerSettings,
      CompilerInfoCacheAdapter compilerInfoCache,
      OCLanguageKind kind,
      VirtualFile vf,
      ImmutableCollection<String> globalDefines) {
    return null;
  }

  protected void fillFileMacrosInternal(
      Project project,
      OCCompilerSettings compilerSettings,
      OCInclusionContext context,
      PsiFile sourceFile,
      CompilerInfoCacheAdapter compilerInfoCache,
      ImmutableMap<String, String> globalFeatures) {}
}
