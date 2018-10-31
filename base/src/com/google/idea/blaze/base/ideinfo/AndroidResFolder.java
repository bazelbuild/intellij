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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.ResFolderLocation;
import java.util.Collection;

/**
 * Information about Android res folders. Contains the root res folder and optionally the specific
 * resources within that folder.
 */
public final class AndroidResFolder implements ProtoWrapper<IntellijIdeInfo.ResFolderLocation> {
  private final ArtifactLocation root;
  private final ImmutableSet<String> resources;

  private AndroidResFolder(ArtifactLocation root, ImmutableSet<String> resources) {
    this.root = root;
    this.resources = resources;
  }

  static AndroidResFolder fromProto(IntellijIdeInfo.ResFolderLocation proto) {
    return new AndroidResFolder(
        ArtifactLocation.fromProto(proto.getRoot()), ImmutableSet.copyOf(proto.getResourcesList()));
  }

  static AndroidResFolder fromProto(Common.ArtifactLocation root) {
    return new AndroidResFolder(ArtifactLocation.fromProto(root), ImmutableSet.of());
  }

  @Override
  public ResFolderLocation toProto() {
    return IntellijIdeInfo.ResFolderLocation.newBuilder()
        .setRoot(root.toProto())
        .addAllResources(resources)
        .build();
  }

  public ArtifactLocation getRoot() {
    return root;
  }

  public ImmutableSet<String> getResources() {
    return resources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for an resource artifact location */
  public static class Builder {
    ArtifactLocation root;
    ImmutableSet.Builder<String> resources = ImmutableSet.builder();

    public AndroidResFolder.Builder setRoot(ArtifactLocation root) {
      this.root = root;
      return this;
    }

    public AndroidResFolder.Builder addResource(String resource) {
      resources.add(resource);
      return this;
    }

    public AndroidResFolder.Builder addResources(Collection<String> resources) {
      this.resources.addAll(resources);
      return this;
    }

    public AndroidResFolder build() {
      return new AndroidResFolder(root, resources.build());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AndroidResFolder that = (AndroidResFolder) o;
    return Objects.equal(getRoot(), that.getRoot())
        && Objects.equal(getResources(), that.getResources());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getRoot(), getResources());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("AndroidResFolder {");
    sb.append("\n  root = ").append(getRoot());
    if (!resources.isEmpty()) {
      sb.append("\n  resources = ").append(String.join(",", resources));
    }
    sb.append("}");
    return sb.toString();
  }
}
