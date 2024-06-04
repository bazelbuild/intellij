/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.scala;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil;
import scala.Option;

/** Provides SDK compatibility shims for Scala classes, available to IntelliJ CE & UE. */
public class ScalaCompat {
  private ScalaCompat() {}

  /** #api213: Inline the call. Method location and signature changed in 2021.2 */
  public static Option<PsiMethod> findMainMethod(@NotNull ScObject obj) {
    return ScalaMainMethodUtil.findScala2MainMethod(obj);
  }

  /** #api213: Inline the call. Method location and signature changed in 2021.2 */
  public static boolean hasMainMethod(@NotNull ScObject obj) {
    return ScalaMainMethodUtil.hasScala2MainMethod(obj);
  }
}
