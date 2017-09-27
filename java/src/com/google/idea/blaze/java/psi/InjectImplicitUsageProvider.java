/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.psi;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;

/** Suppresses 'never assigned' warnings for @Inject annotated fields. */
public class InjectImplicitUsageProvider implements ImplicitUsageProvider {

  private static final String JSR_330_INJECT_ANNOTATION = "javax.inject.Inject";
  private static final String GUICE_INJECT_ANNOTATION = "com.google.inject.Inject";

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return element instanceof PsiField && hasAnnotation((PsiField) element);
  }

  private static boolean hasAnnotation(PsiField field) {
    PsiModifierList modifiers = field.getModifierList();
    if (modifiers == null) {
      return false;
    }
    return modifiers.findAnnotation(JSR_330_INJECT_ANNOTATION) != null
        || modifiers.findAnnotation(GUICE_INJECT_ANNOTATION) != null;
  }
}
