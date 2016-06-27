/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Immutable
public final class AndroidResourceModule implements Serializable {
  private static final long serialVersionUID = 5L;

  public final Label label;
  public final ImmutableCollection<File> resources;
  public final ImmutableCollection<File> transitiveResources;
  public final ImmutableCollection<Label> transitiveResourceDependencies;

  public AndroidResourceModule(Label label,
                               ImmutableCollection<File> resources,
                               ImmutableCollection<File> transitiveResources,
                               ImmutableCollection<Label> transitiveResourceDependencies) {
    this.label = label;
    this.resources = resources;
    this.transitiveResources = transitiveResources;
    this.transitiveResourceDependencies = transitiveResourceDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AndroidResourceModule) {
      AndroidResourceModule that = (AndroidResourceModule)o;
      return Objects.equal(this.label, that.label)
             && Objects.equal(this.resources, that.resources)
             && Objects.equal(this.transitiveResources, that.transitiveResources)
             && Objects.equal(this.transitiveResourceDependencies, that.transitiveResourceDependencies);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
      this.label,
      this.resources,
      this.transitiveResources,
      this.transitiveResourceDependencies
    );
  }

  @Override
  public String toString() {
    return "AndroidResourceModule{" + "\n"
           + "  label: " + label + "\n"
           + "  resources: " + resources + "\n"
           + "  transitiveResources: " + transitiveResources + "\n"
           + "  transitiveResourceDependencies: " + transitiveResourceDependencies + "\n"
           + '}';
  }

  public static Builder builder(Label label) {
    return new Builder(label);
  }

  public boolean isEmpty() {
    return resources.isEmpty() && transitiveResources.isEmpty();
  }

  public static class Builder {
    private final Label label;
    private final Set<ArtifactLocation> resources = Sets.newHashSet();
    private final Set<ArtifactLocation> transitiveResources = Sets.newHashSet();
    private Set<Label> transitiveResourceDependencies = Sets.newHashSet();

    public Builder(Label label) {
      this.label = label;
    }

    public Builder addResource(ArtifactLocation resource) {
      this.resources.add(resource);
      return this;
    }

    public Builder addAllResources(List<ArtifactLocation> resources) {
      this.resources.addAll(resources);
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

    public Builder addTransitiveResourceDependency(Label dependency) {
      this.transitiveResourceDependencies.add(dependency);
      return this;
    }

    public Builder addTransitiveResourceDependency(String dependency) {
      return addTransitiveResourceDependency(new Label(dependency));
    }

    @NotNull
    public AndroidResourceModule build() {
      return new AndroidResourceModule(
        label,
        ImmutableList.copyOf(
          resources
            .stream()
            .map(ArtifactLocation::getFile)
            .sorted()
            .collect(Collectors.toList())),
        ImmutableList.copyOf(
          transitiveResources
            .stream()
            .map(ArtifactLocation::getFile)
            .sorted()
            .collect(Collectors.toList())),
        ImmutableList.copyOf(
          transitiveResourceDependencies
            .stream()
            .sorted()
            .collect(Collectors.toList()))
      );
    }
  }
}
