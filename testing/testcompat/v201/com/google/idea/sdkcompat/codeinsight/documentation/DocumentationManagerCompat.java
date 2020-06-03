/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.codeinsight.documentation;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.psi.PsiElement;

/** Compat class for {@link DocumentationManager} */
public class DocumentationManagerCompat {
  private DocumentationManagerCompat() {}

  public static String generateDocumentation(
      DocumentationManager documentationManager, PsiElement element, PsiElement originalElement) {
    return generateDocumentation(documentationManager, element, originalElement, false);
  }

  public static String generateDocumentation(
      DocumentationManager documentationManager,
      PsiElement element,
      PsiElement originalElement,
      boolean onHover) {
    return documentationManager.generateDocumentation(element, originalElement, onHover);
  }
}
