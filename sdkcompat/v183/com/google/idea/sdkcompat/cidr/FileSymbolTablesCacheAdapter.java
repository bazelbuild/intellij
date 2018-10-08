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
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties.SymbolsKind;
import com.jetbrains.cidr.lang.symbols.symtable.OCSymbolTablesBuildingActivity;

/** Adapter to bridge different SDK versions. */
public class FileSymbolTablesCacheAdapter {

  @VisibleForTesting
  public static void enableSymbolTableBuildingInTests(
      Project project, boolean loadPrevSymbols, boolean saveSymbols) {
    FileSymbolTablesCache.setShouldBuildTablesInTests(
        new FileSymbolTablesCache.SymbolsProperties(
            // We may want to allow ALL_INCLUDING_UNUSED_SYSTEM_HEADERS as well, but for now
            // ONLY_USED is enough
            SymbolsKind.ONLY_USED, loadPrevSymbols, saveSymbols));

    // Normally, CidrProjectFixture will setShouldBuildTablesInTests before a project is created
    // and thus before FileSymbolTablesCache is instantiated for a project.
    //
    // When FileSymbolTablesCache is created, it should queue OCSymbolTablesBuildingActivity.
    //
    // However, because we run well after a project is created, we need to manually queue
    // OCSymbolTablesBuildingActivity after toggling on setShouldBuildTablesInTests.
    OCSymbolTablesBuildingActivity.getInstance(project).rebuildSymbols();
  }

  @VisibleForTesting
  public static void disableSymbolTableBuildingInTests() {
    FileSymbolTablesCache.setShouldBuildTablesInTests(null);
  }
}
