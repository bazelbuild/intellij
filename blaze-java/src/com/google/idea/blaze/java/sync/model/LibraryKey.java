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
package com.google.idea.blaze.java.sync.model;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Uniquely identifies a library as imported into IntellJ.
 */
@Immutable
public final class LibraryKey implements Serializable {
  public static final long serialVersionUID = 1L;

  public static final Comparator<LibraryKey> COMPARATOR = new Comparator<LibraryKey>() {
    @Override
    public int compare(LibraryKey o1, LibraryKey o2) {
      return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
    }
  };

  @NotNull
  private final String name;

  @NotNull
  public static LibraryKey fromJarFile(@NotNull File jarFile) {
    int parentHash = jarFile.getParent().hashCode();
    String name = FileUtil.getNameWithoutExtension(jarFile) + "_" + Integer.toHexString(parentHash);
    return new LibraryKey(name);
  }

  @NotNull
  public static LibraryKey forResourceLibrary() {
    return new LibraryKey("external_resources_library");
  }

  @NotNull
  public static LibraryKey fromIntelliJLibrary(@NotNull Library library) {
    String name = library.getName();
    if (name == null) {
      throw new IllegalArgumentException("Null library name");
    }
    return fromIntelliJLibraryName(name);
  }

  @NotNull
  public static LibraryKey fromIntelliJLibraryName(@NotNull String name) {
    return new LibraryKey(name);
  }

  LibraryKey(@NotNull String name) {
    this.name = name;
  }

  public String getIntelliJLibraryName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LibraryKey that = (LibraryKey)o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
