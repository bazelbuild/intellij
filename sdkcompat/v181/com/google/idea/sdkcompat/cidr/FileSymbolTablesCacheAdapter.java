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
package com.google.idea.sdkcompat.cidr;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;

/** Adapter to bridge different SDK versions. */
public class FileSymbolTablesCacheAdapter {

  @VisibleForTesting
  public static void enableSymbolTableBuildingInTests(
      Project project, boolean loadPrevSymbols, boolean saveSymbols) {
    FileSymbolTablesCache.setShouldBuildTablesInTests(
        /* build=*/ true, /* doNotReloadExistingSymbols= */ !loadPrevSymbols);
  }

  @VisibleForTesting
  public static void disableSymbolTableBuildingInTests() {
    FileSymbolTablesCache.setShouldBuildTablesInTests(false, true);
  }
}
