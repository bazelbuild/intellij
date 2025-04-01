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
package com.google.idea.blaze.javascript;

import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryKindRegistry;
import com.intellij.util.LazyInitializer;

import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Prevents garbage collection of javascript libraries. */
class BlazeJavascriptLibrarySource extends LibrarySource.Adapter {

  static final BlazeJavascriptLibrarySource INSTANCE = new BlazeJavascriptLibrarySource();

  private BlazeJavascriptLibrarySource() {}

  static final LazyInitializer.LazyValue<LibraryKind> JS_LIBRARY_KIND = LazyInitializer.create(
          () -> LibraryKindRegistry.getInstance().findKindById("javaScript")
  );

  @Nullable
  @Override
  public Predicate<Library> getGcRetentionFilter() {
    if (JS_LIBRARY_KIND.get() == null) {
      return null;
    }
    return BlazeJavascriptLibrarySource::isJavascriptLibrary;
  }

  static boolean isJavascriptLibrary(Library library) {
    return JS_LIBRARY_KIND.get() != null
        && library instanceof LibraryEx
        && JS_LIBRARY_KIND.get().equals(((LibraryEx) library).getKind());
  }
}
