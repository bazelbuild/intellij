/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.roots.libraries.Library;
import java.util.List;
import java.util.function.Predicate;

/** Prevents garbage collection of Go libraries */
class BlazeGoLibrarySource extends LibrarySource.Adapter {
  private final BlazeGoLibrary library;

  BlazeGoLibrarySource(BlazeProjectData projectData) {
    this.library = new BlazeGoLibrary(projectData);
  }

  @Override
  public List<? extends BlazeLibrary> getLibraries() {
    if (BlazeGoLibrary.useGoLibrary.getValue()) {
      return ImmutableList.of(library);
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public Predicate<Library> getGcRetentionFilter() {
    return BlazeGoLibrarySource::isGoLibrary;
  }

  static boolean isGoLibrary(Library library) {
    String name = library.getName();
    if (name == null) {
      return false;
    }
    for (String prefix : BlazeGoSyncPlugin.GO_LIBRARY_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
