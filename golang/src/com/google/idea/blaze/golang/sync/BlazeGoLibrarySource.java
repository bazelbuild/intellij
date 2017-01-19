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

import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.roots.libraries.Library;
import java.util.function.Predicate;

/** Prevents garbage collection of Go libraries */
class BlazeGoLibrarySource extends LibrarySource.Adapter {

  static final BlazeGoLibrarySource INSTANCE = new BlazeGoLibrarySource();

  private BlazeGoLibrarySource() {}

  @Override
  public Predicate<Library> getGcRetentionFilter() {
    return BlazeGoLibrarySource::isGoLibrary;
  }

  static boolean isGoLibrary(Library library) {
    String name = library.getName();
    return name != null && name.startsWith(BlazeGoSyncPlugin.GO_LIBRARY_PREFIX);
  }
}
