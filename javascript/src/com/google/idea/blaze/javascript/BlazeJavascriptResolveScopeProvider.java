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
package com.google.idea.blaze.javascript;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.libraries.ExternalLibraryManager;
import com.google.idea.blaze.typescript.BlazeTypeScriptAdditionalLibraryRootsProvider;
import com.intellij.lang.javascript.psi.resolve.JSElementResolveScopeProvider;
import com.intellij.lang.javascript.psi.resolve.JSResolveScopeProvider;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Augments {@link JSResolveUtil#getResolveScope} so {@link AdditionalLibraryRootsProvider}s can
 * resolve.
 */
class BlazeJavascriptResolveScopeProvider implements JSElementResolveScopeProvider {
  @Nullable
  @Override
  public GlobalSearchScope getElementResolveScope(PsiElement element) {
    Project project = element.getProject();
    if (!Blaze.isBlazeProject(project)) {
      return null;
    }
    ExternalLibraryManager manager = ExternalLibraryManager.getInstance(project);
    List<SyntheticLibrary> libraries =
        ImmutableList.<SyntheticLibrary>builder()
            .addAll(manager.getLibrary(BlazeJavascriptAdditionalLibraryRootsProvider.class))
            .addAll(manager.getLibrary(BlazeTypeScriptAdditionalLibraryRootsProvider.class))
            .build();
    if (libraries.isEmpty()) {
      return null;
    }
    GlobalSearchScope baseScope = getBaseScope(element);
    if (baseScope == null) {
      return null;
    }
    return new DelegatingGlobalSearchScope(baseScope) {
      @Override
      public boolean contains(VirtualFile file) {
        return super.contains(file)
            || libraries.stream().anyMatch(library -> library.contains(file));
      }
    };
  }

  /** See {@link JSResolveUtil#getResolveScope(PsiElement)}. */
  @Nullable
  private static GlobalSearchScope getBaseScope(PsiElement element) {
    VirtualFile file = JSResolveScopeProvider.getFileForScopeEvaluation(element);
    if (file == null) {
      return null;
    }
    return ResolveScopeManager.getInstance(element.getProject()).getDefaultResolveScope(file);
  }
}
