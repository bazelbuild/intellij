/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.java;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * #api222 interface is different in 223, inline when 222 support is dropped
 * */
public abstract class AttachSourcesProviderAdapter
    implements AttachSourcesProvider {

    public abstract Collection<AttachSourcesAction> getAdapterActions(
            List<? extends LibraryOrderEntry> orderEntries, final PsiFile psiFile);


  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(
      List<? extends LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
      return getAdapterActions(orderEntries, psiFile);
  }

    public static abstract class AttachSourcesActionAdapter implements AttachSourcesAction {
        public abstract ActionCallback adapterPerform(List<? extends LibraryOrderEntry> orderEntriesContainingFile);


        public ActionCallback perform(List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
            return adapterPerform(orderEntriesContainingFile);
        }
    }
}
