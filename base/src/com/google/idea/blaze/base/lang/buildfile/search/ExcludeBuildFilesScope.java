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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UseScopeOptimizer;
import javax.annotation.Nullable;

/**
 * Causes all calls to PsiSearchHelper.getUseScope to exclude BUILD files, when searching for files.
 * <br>
 * BUILD file / BUILD package references are handled by a separate reference searcher.
 *
 * <p>This is a hack, but greatly improves efficiency. The reasoning behind this: - BUILD files have
 * very strict file reference patterns, and very narrow direct reference scopes (a package can't
 * directly reference files in another package). - IJ *constantly* performs global searches on
 * strings when manipulating files (e.g. searching for file uses for highlighting, rename, move
 * operations). This causes us to re-parse every BUILD file in the project, multiple times.
 */
public class ExcludeBuildFilesScope extends UseScopeOptimizer {

  @Nullable
  @Override
  public GlobalSearchScope getScopeToExclude(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return GlobalSearchScope.getScopeRestrictedByFileTypes(
          new EverythingGlobalScope(), BuildFileType.INSTANCE);
    }
    return null;
  }
}
