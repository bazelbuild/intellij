/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.roots.libraries.Library;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Prevents garbage collection of Kotlin libraries. */
class BlazeKotlinLibrarySource extends LibrarySource.Adapter {
  private final BlazeProjectData blazeProjectData;

  BlazeKotlinLibrarySource(BlazeProjectData blazeProjectData) {
    this.blazeProjectData = blazeProjectData;
  }

  @Nullable
  @Override
  public Predicate<Library> getGcRetentionFilter() {
    return library -> {
      String libraryName = library.getName();
      return libraryName != null
          && libraryName.equals(KotlinSdkUtils.KOTLIN_JAVA_RUNTIME_LIBRARY_NAME);
    };
  }

  @Override
  public List<? extends BlazeLibrary> getLibraries() {
    BlazeKotlinSyncData syncData = blazeProjectData.syncState.get(BlazeKotlinSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.importResult.libraries.values().asList();
  }
}
