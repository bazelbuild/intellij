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
package com.google.idea.blaze.android.sync.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.NotNull;

/**
 * An android resource module. Maps from an android_library's resources to an Android Studio
 * module/facet.
 */
@Immutable
public final class AndroidResourceModule
    implements ProtoWrapper<ProjectData.AndroidResourceModule> {
  public final TargetKey targetKey;
  public final ImmutableList<ArtifactLocation> resources;
  public final ImmutableList<ArtifactLocation> transitiveResources;
  public final ImmutableList<String> resourceLibraryKeys;
  public final ImmutableList<TargetKey> transitiveResourceDependencies;

  public AndroidResourceModule(
      TargetKey targetKey,
      ImmutableList<ArtifactLocation> resources,
      ImmutableList<ArtifactLocation> transitiveResources,
      ImmutableList<String> resourceLibraryKeys,
      ImmutableList<TargetKey> transitiveResourceDependencies) {
    this.targetKey = targetKey;
    this.resources = resources;
    this.transitiveResources = transitiveResources;
    this.resourceLibraryKeys = resourceLibraryKeys;
    this.transitiveResourceDependencies = transitiveResourceDependencies;
  }

  static AndroidResourceModule fromProto(ProjectData.AndroidResourceModule proto) {
    return new AndroidResourceModule(
        TargetKey.fromProto(proto.getTargetKey()),
        ProtoWrapper.map(proto.getResourcesList(), ArtifactLocation::fromProto),
        ProtoWrapper.map(proto.getTransitiveResourcesList(), ArtifactLocation::fromProto),
        ImmutableList.copyOf(proto.getResourceLibraryKeysList()),
        ProtoWrapper.map(proto.getTransitiveResourceDependenciesList(), TargetKey::fromProto));
  }

  @Override
  public ProjectData.AndroidResourceModule toProto() {
    return ProjectData.AndroidResourceModule.newBuilder()
        .setTargetKey(targetKey.toProto())
        .addAllResources(ProtoWrapper.mapToProtos(resources))
        .addAllTransitiveResources(ProtoWrapper.mapToProtos(transitiveResources))
        .addAllResourceLibraryKeys(resourceLibraryKeys)
        .addAllTransitiveResourceDependencies(
            ProtoWrapper.mapToProtos(transitiveResourceDependencies))
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AndroidResourceModule) {
      AndroidResourceModule that = (AndroidResourceModule) o;
      return Objects.equal(this.targetKey, that.targetKey)
          && Objects.equal(this.resources, that.resources)
          && Objects.equal(this.transitiveResources, that.transitiveResources)
          && Objects.equal(this.resourceLibraryKeys, that.resourceLibraryKeys)
          && Objects.equal(
              this.transitiveResourceDependencies, that.transitiveResourceDependencies);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        this.targetKey,
        this.resources,
        this.transitiveResources,
        this.resourceLibraryKeys,
        this.transitiveResourceDependencies);
  }

  @Override
  public String toString() {
    return "AndroidResourceModule{"
        + "\n"
        + "  rule: "
        + targetKey
        + "\n"
        + "  resources: "
        + resources
        + "\n"
        + "  transitiveResources: "
        + transitiveResources
        + "\n"
        + "  aarLibraries: "
        + resourceLibraryKeys
        + "\n"
        + "  transitiveResourceDependencies: "
        + transitiveResourceDependencies
        + "\n"
        + '}';
  }

  public static Builder builder(TargetKey targetKey) {
    return new Builder(targetKey);
  }

  public boolean isEmpty() {
    return resources.isEmpty() && transitiveResources.isEmpty() && resourceLibraryKeys.isEmpty();
  }

  /** Builder for the resource module */
  public static class Builder {
    private final TargetKey targetKey;
    private final Set<ArtifactLocation> resources = Sets.newHashSet();
    private final Set<ArtifactLocation> transitiveResources = Sets.newHashSet();
    private final Set<String> resourceLibraryKeys = Sets.newHashSet();
    private Set<TargetKey> transitiveResourceDependencies = Sets.newHashSet();

    public Builder(TargetKey targetKey) {
      this.targetKey = targetKey;
    }

    public Builder addResource(ArtifactLocation resource) {
      this.resources.add(resource);
      return this;
    }

    public Builder addAllResources(List<ArtifactLocation> resources) {
      this.resources.addAll(resources);
      return this;
    }

    public Builder addResourceLibraryKey(String aarLibraryKey) {
      this.resourceLibraryKeys.add(aarLibraryKey);
      return this;
    }

    public Builder addResourceAndTransitiveResource(ArtifactLocation resource) {
      this.resources.add(resource);
      this.transitiveResources.add(resource);
      return this;
    }

    public Builder addTransitiveResource(ArtifactLocation resource) {
      this.transitiveResources.add(resource);
      return this;
    }

    public Builder addTransitiveResourceDependency(TargetKey dependency) {
      this.transitiveResourceDependencies.add(dependency);
      return this;
    }

    public Builder addTransitiveResourceDependency(Label dependency) {
      this.transitiveResourceDependencies.add(TargetKey.forPlainTarget(dependency));
      return this;
    }

    public Builder addTransitiveResourceDependency(String dependency) {
      return addTransitiveResourceDependency(Label.create(dependency));
    }

    @NotNull
    public AndroidResourceModule build() {
      return new AndroidResourceModule(
          targetKey,
          ImmutableList.sortedCopyOf(resources),
          ImmutableList.sortedCopyOf(transitiveResources),
          ImmutableList.sortedCopyOf(resourceLibraryKeys),
          ImmutableList.sortedCopyOf(transitiveResourceDependencies));
    }
  }
}
