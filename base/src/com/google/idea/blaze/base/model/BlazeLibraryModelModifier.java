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
package com.google.idea.blaze.base.model;

import com.intellij.openapi.roots.libraries.Library;

/** Provides ability to modify library content in {@link Library.ModifiableModel}. */
public interface BlazeLibraryModelModifier {
  /** Writes the library content to its {@link Library.ModifiableModel}. */
  void updateModifiableModel();

  /** Returns the name of library that the modifier will update. */
  String getName();
}
