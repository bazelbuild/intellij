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
package com.google.idea.sdkcompat.cidr;

import com.intellij.psi.PsiElement;
import com.jetbrains.cidr.lang.refactoring.util.OCChangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Compat methods for {@link OCChangeUtil} */
public class OCChangeUtilCompat {
  /**
   * Passthrough to {@link OCChangeUtil#addHandlingMacros(PsiElement, PsiElement, PsiElement,
   * boolean)}.
   *
   * <p>#api191
   */
  public static PsiElement addHandlingMacros(
      @NotNull PsiElement parent,
      @NotNull PsiElement child,
      @Nullable PsiElement anchor,
      boolean isBefore) {
    return OCChangeUtil.addHandlingMacros(parent, child, anchor, isBefore);
  }
}
