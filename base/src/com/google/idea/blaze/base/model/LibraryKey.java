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
package com.google.idea.blaze.base.model;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/** Uniquely identifies a library as imported into IntellJ. */
@Immutable
public final class LibraryKey implements Serializable {
  public static final long serialVersionUID = 1L;

  private final String name;

  public static LibraryKey fromJarFile(ArtifactLocation artifactLocation) {
    File jarFile = new File(artifactLocation.getExecutionRootRelativePath());
    String parent = jarFile.getParent();
    int parentHash = parent != null ? parent.hashCode() : jarFile.hashCode();
    String name = FileUtil.getNameWithoutExtension(jarFile) + "_" + Integer.toHexString(parentHash);
    return new LibraryKey(name);
  }

  public static LibraryKey forResourceLibrary() {
    return new LibraryKey("external_resources_library");
  }

  public static LibraryKey fromIntelliJLibrary(Library library) {
    String name = library.getName();
    if (name == null) {
      throw new IllegalArgumentException("Null library name");
    }
    return fromIntelliJLibraryName(name);
  }

  public static LibraryKey fromIntelliJLibraryName(String name) {
    return new LibraryKey(name);
  }

  LibraryKey(String name) {
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

    LibraryKey that = (LibraryKey) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
