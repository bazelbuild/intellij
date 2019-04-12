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
package com.google.idea.sdkcompat.javascript;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.CachedValueProvider.Result;
import javax.annotation.Nullable;

/** Compatibility layer for Angular2ContextProvider. #api183 */
public interface Angular2ContextProviderCompat {
  Result<Boolean> isAngular2Context(PsiDirectory psiDirectory);

  static boolean hasEp() {
    return false;
  }

  @Nullable
  static ExtensionPointName<Angular2ContextProviderCompat> getEpName() {
    return null;
  }

  @Nullable
  static Class<Angular2ContextProviderCompat> getEpType() {
    return null;
  }
}
