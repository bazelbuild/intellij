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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.symbols.symtable.SymbolTableProvider;

/**
 * Compat methods for {@link SymbolTableProvider}.
 *
 * <p>#api191
 */
public class SymbolTableProviderCompat {
  /** Returns whether the given file belongs to the project. #api191. */
  public static boolean isSourceFile(Project project, VirtualFile modifiedFile) {
    return SymbolTableProvider.isSourceFile(project, modifiedFile);
  }
}
