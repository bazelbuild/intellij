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
package com.google.idea.sdkcompat.python;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import javax.annotation.Nullable;

/** Compat for {@link PyTypeProviderBase} #api181 */
public abstract class PyTypeProviderBaseAdapter extends PyTypeProviderBase {
  @Override
  public final PyType getReferenceType(
      PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    return getReferenceTypeImpl(referenceTarget, context, anchor).get();
  }

  @Nullable
  protected abstract Ref<PyType> getReferenceTypeImpl(
      PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor);
}
