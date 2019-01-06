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
package com.google.idea.blaze.scala.libraries;

import com.google.idea.blaze.java.libraries.BlazeAttachSourceProvider;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Adapts {@link BlazeAttachSourceProvider} to Scala libraries.
 *
 * @see BlazeAttachSourceProvider
 */
public class BlazeScalaAttachSourceProvider implements AttachSourcesProvider {

  private final AttachSourcesProvider delegate = new BlazeAttachSourceProvider(new BlazeScalaJarLibraryLocator());

  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> list, PsiFile psiFile) {
    return delegate.getActions(list, psiFile);
  }
}
