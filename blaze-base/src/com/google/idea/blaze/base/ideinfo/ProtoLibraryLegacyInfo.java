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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.Collection;

/**
 * Proto library info for legacy proto libraries.
 *
 * Replicates blaze semantics.
 */
public class ProtoLibraryLegacyInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum ApiFlavor {
    VERSION_1,
    MUTABLE,
    IMMUTABLE,
    BOTH,
    NONE,
  }

  public final ApiFlavor apiFlavor;

  public final Collection<LibraryArtifact> jarsV1;
  public final Collection<LibraryArtifact> jarsMutable;
  public final Collection<LibraryArtifact> jarsImmutable;

  public ProtoLibraryLegacyInfo(ApiFlavor apiFlavor,
                                Collection<LibraryArtifact> jarsV1,
                                Collection<LibraryArtifact> jarsMutable,
                                Collection<LibraryArtifact> jarsImmutable) {
    this.apiFlavor = apiFlavor;
    this.jarsV1 = jarsV1;
    this.jarsMutable = jarsMutable;
    this.jarsImmutable = jarsImmutable;
  }

  public static Builder builder(ApiFlavor apiFlavor) {
    return new Builder(apiFlavor);
  }

  public static class Builder {
    private final ApiFlavor apiFlavor;
    private ImmutableList.Builder<LibraryArtifact> jarsV1 = ImmutableList.builder();
    private ImmutableList.Builder<LibraryArtifact> jarsMutable = ImmutableList.builder();
    private ImmutableList.Builder<LibraryArtifact> jarsImmutable = ImmutableList.builder();

    Builder(ApiFlavor apiFlavor) {
      this.apiFlavor = apiFlavor;
    }

    public Builder addJarV1(LibraryArtifact.Builder library) {
      jarsV1.add(library.build());
      return this;
    }

    public Builder addJarMutable(LibraryArtifact.Builder library) {
      jarsMutable.add(library.build());
      return this;
    }

    public Builder addJarImmutable(LibraryArtifact.Builder library) {
      jarsImmutable.add(library.build());
      return this;
    }

    public ProtoLibraryLegacyInfo build() {
      return new ProtoLibraryLegacyInfo(apiFlavor, jarsV1.build(), jarsMutable.build(), jarsImmutable.build());
    }
  }
}
