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

import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;

/** Compat methods for {@link CLanguageKind}. */
public class CLanguageKindCompat {
  /**
   * Returns {@link CLanguageKind#C}.
   *
   * <p>#api183: The enum definition was moved from {@link OCLanguageKind} to {@link CLanguageKind}
   * in api191.
   */
  public static OCLanguageKind c() {
    return CLanguageKind.C;
  }

  /**
   * Returns {@link CLanguageKind#CPP}.
   *
   * <p>#api183: The enum definition was moved from {@link OCLanguageKind} to {@link CLanguageKind}
   * in api191.
   */
  public static OCLanguageKind cpp() {
    return CLanguageKind.CPP;
  }
}
