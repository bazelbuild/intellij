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
package com.google.idea.sdkcompat.python;

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferenceSkipperExtPoint;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import org.jetbrains.annotations.NotNull;

/** PyUnresolvedReferenceSkipperExtPoint has been removed in v193. #api192 */
public abstract class PyInspectionExtensionCompat implements PyUnresolvedReferenceSkipperExtPoint {
  @Override
  public boolean unusedImportShouldBeSkipped(PyImportedNameDefiner pyImportedNameDefiner) {
    return ignoreUnusedImports(pyImportedNameDefiner);
  }

  public abstract boolean ignoreUnusedImports(@NotNull PyImportedNameDefiner importNameDefiner);
}
