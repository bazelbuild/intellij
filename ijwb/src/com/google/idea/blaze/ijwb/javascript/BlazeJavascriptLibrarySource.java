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
package com.google.idea.blaze.ijwb.javascript;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Prevents garbage collection of javascript libraries and produces {@link BlazeJavascriptLibrary}.
 */
class BlazeJavascriptLibrarySource extends LibrarySource.Adapter {

  @Nullable static final LibraryKind JS_LIBRARY_KIND = LibraryKind.findById("javaScript");

  private final BlazeJavascriptLibrary library;

  BlazeJavascriptLibrarySource(BlazeProjectData blazeProjectData) {
    library = new BlazeJavascriptLibrary(blazeProjectData);
  }

  @Override
  public List<? extends BlazeLibrary> getLibraries() {
    return ImmutableList.of(library);
  }

  @Nullable
  @Override
  public Predicate<Library> getGcRetentionFilter() {
    if (JS_LIBRARY_KIND == null) {
      return null;
    }
    return BlazeJavascriptLibrarySource::isJavascriptLibrary;
  }

  static boolean isJavascriptLibrary(Library library) {
    return JS_LIBRARY_KIND != null
        && library instanceof LibraryEx
        && JS_LIBRARY_KIND.equals(((LibraryEx) library).getKind());
  }
}
