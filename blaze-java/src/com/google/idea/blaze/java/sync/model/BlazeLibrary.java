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

import com.google.common.base.Objects;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * An immutable reference to a .jar required by a rule. This class supports value semantics when
 * used as a key to a hash map.
 */
@Immutable
public final class BlazeLibrary implements Serializable {
  private static final long serialVersionUID = 6L;

  @NotNull
  private final LibraryKey key;

  @Nullable
  private final LibraryArtifact libraryArtifact;

  @Nullable
  private final Collection<File> sources;

  public BlazeLibrary(
    @NotNull LibraryKey key,
    @NotNull LibraryArtifact libraryArtifact) {
    this(key, libraryArtifact, null);
  }

  public BlazeLibrary(
    @NotNull LibraryKey key,
    @NotNull Collection<File> sources) {
    this(key, null, sources);
  }

  private BlazeLibrary(
    @NotNull LibraryKey key,
    @Nullable LibraryArtifact libraryArtifact,
    @Nullable Collection<File> sources) {
    this.key = key;
    this.libraryArtifact = libraryArtifact;
    this.sources = sources;
  }

  /**
   * Returns the library key.
   */
  @NotNull
  public LibraryKey getKey() {
    return key;
  }

  @Nullable
  public LibraryArtifact getLibraryArtifact() {
    return libraryArtifact;
  }

  @Nullable
  public Collection<File> getSources() {
    return sources;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key, libraryArtifact, sources);
  }

  @Override
  public String toString() {
    return key.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeLibrary)) {
      return false;
    }

    BlazeLibrary that = (BlazeLibrary)other;

    return Objects.equal(key, that.key)
           && Objects.equal(libraryArtifact, that.libraryArtifact)
           && Objects.equal(sources, that.sources);
  }
}
