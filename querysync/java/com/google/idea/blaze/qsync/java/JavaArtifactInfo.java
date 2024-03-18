/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Information about a project dependency that is calculated when the dependency is built. */
@AutoValue
public abstract class JavaArtifactInfo {

  /** Build label for the dependency. */
  public abstract Label label();

  /**
   * The jar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableList<BuildArtifact> jars();

  /**
   * The aar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableList<BuildArtifact> ideAars();

  /**
   * The gensrc artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in
   * the cache.
   */
  public abstract ImmutableList<BuildArtifact> genSrcs();

  /** Workspace relative sources for this dependency, extracted at dependency build time. */
  public abstract ImmutableSet<Path> sources();

  public abstract ImmutableSet<Path> srcJars();

  public abstract String androidResourcesPackage();

  public static Builder builder() {
    return new AutoValue_JavaArtifactInfo.Builder();
  }

  public static JavaArtifactInfo create(
      JavaTargetArtifacts proto, Function<Path, String> digestMap) {
    // Note, the proto contains a list of sources, we take the parent as we want directories instead
    Label target = Label.of(proto.getTarget());
    return builder()
        .setLabel(target)
        .setJars(toArtifacts(proto.getJarsList(), digestMap, target))
        .setIdeAars(toArtifacts(proto.getIdeAarsList(), digestMap, target))
        .setGenSrcs(toArtifacts(proto.getGenSrcsList(), digestMap, target))
        .setSources(proto.getSrcsList().stream().map(Interners::pathOf).collect(toImmutableSet()))
        .setSrcJars(
            proto.getSrcjarsList().stream().map(Interners::pathOf).collect(toImmutableSet()))
        .setAndroidResourcesPackage(proto.getAndroidResourcesPackage())
        .build();
  }

  private static ImmutableList<BuildArtifact> toArtifacts(
      List<String> paths, Function<Path, String> digestMap, Label target) {
    return paths.stream()
        .map(Interners::pathOf)
        .map(p -> BuildArtifact.create(p, target, digestMap))
        .collect(toImmutableList());
  }

  public JavaTargetArtifacts toProto() {
    return JavaTargetArtifacts.newBuilder()
        .setTarget(label().toString())
        .addAllJars(toPathStrings(jars()))
        .addAllIdeAars(toPathStrings(ideAars()))
        .addAllGenSrcs(toPathStrings(genSrcs()))
        .addAllSrcs(sources().stream().map(Path::toString).collect(toImmutableList()))
        .addAllSrcjars(srcJars().stream().map(Path::toString).collect(toImmutableList()))
        .setAndroidResourcesPackage(androidResourcesPackage())
        .build();
  }

  private static ImmutableList<String> toPathStrings(ImmutableList<BuildArtifact> artifacts) {
    return artifacts.stream()
        .map(BuildArtifact::path)
        .map(Path::toString)
        .collect(toImmutableList());
  }

  public final boolean containsPath(Path artifactPath) {
    Predicate<BuildArtifact> pathMatch = a -> a.path().equals(artifactPath);
    return jars().stream().anyMatch(pathMatch)
        || ideAars().stream().anyMatch(pathMatch)
        || genSrcs().stream().anyMatch(pathMatch);
  }

  public final Stream<Path> artifactStream() {
    return Streams.concat(jars().stream(), ideAars().stream(), genSrcs().stream())
        .map(BuildArtifact::path);
  }

  public static JavaArtifactInfo empty(Label target) {
    return builder()
        .setLabel(target)
        .setJars(ImmutableList.of())
        .setIdeAars(ImmutableList.of())
        .setGenSrcs(ImmutableList.of())
        .setSources(ImmutableSet.of())
        .setSrcJars(ImmutableSet.of())
        .setAndroidResourcesPackage("")
        .build();
  }

  /** Builder for {@link JavaArtifactInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setLabel(Label value);

    public abstract Builder setJars(ImmutableList<BuildArtifact> value);

    public abstract Builder setIdeAars(ImmutableList<BuildArtifact> value);

    public abstract Builder setGenSrcs(ImmutableList<BuildArtifact> value);

    public abstract Builder setSources(ImmutableSet<Path> value);

    public abstract Builder setSrcJars(ImmutableSet<Path> value);

    public abstract Builder setAndroidResourcesPackage(String value);

    public abstract JavaArtifactInfo build();
  }
}
