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
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import java.nio.file.Path;
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
  public abstract ImmutableList<Path> jars();

  /**
   * The aar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableList<Path> ideAars();

  /**
   * The gensrc artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in
   * the cache.
   */
  public abstract ImmutableList<Path> genSrcs();

  /** Workspace relative sources for this dependency, extracted at dependency build time. */
  public abstract ImmutableSet<Path> sources();

  public abstract ImmutableSet<Path> srcJars();

  public abstract String androidResourcesPackage();

  public static JavaArtifactInfo create(JavaTargetArtifacts proto) {
    // Note, the proto contains a list of sources, we take the parent as we want directories instead
    return new AutoValue_JavaArtifactInfo(
        Label.of(proto.getTarget()),
        proto.getJarsList().stream().map(Interners::pathOf).collect(toImmutableList()),
        proto.getIdeAarsList().stream().map(Interners::pathOf).collect(toImmutableList()),
        proto.getGenSrcsList().stream().map(Interners::pathOf).collect(toImmutableList()),
        proto.getSrcsList().stream().map(Interners::pathOf).collect(toImmutableSet()),
        proto.getSrcjarsList().stream().map(Interners::pathOf).collect(toImmutableSet()),
        proto.getAndroidResourcesPackage());
  }

  public JavaTargetArtifacts toProto() {
    return JavaTargetArtifacts.newBuilder()
        .setTarget(label().toString())
        .addAllJars(jars().stream().map(Path::toString).collect(toImmutableList()))
        .addAllIdeAars(ideAars().stream().map(Path::toString).collect(toImmutableList()))
        .addAllGenSrcs(genSrcs().stream().map(Path::toString).collect(toImmutableList()))
        .addAllSrcs(sources().stream().map(Path::toString).collect(toImmutableList()))
        .addAllSrcjars(srcJars().stream().map(Path::toString).collect(toImmutableList()))
        .setAndroidResourcesPackage(androidResourcesPackage())
        .build();
  }

  public final boolean containsPath(Path artifactPath) {
    return jars().contains(artifactPath)
        || ideAars().contains(artifactPath)
        || genSrcs().contains(artifactPath);
  }

  public final Stream<Path> artifactStream() {
    return Streams.concat(jars().stream(), ideAars().stream(), genSrcs().stream());
  }

  public static JavaArtifactInfo empty(Label target) {
    return new AutoValue_JavaArtifactInfo(
        target,
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        "");
  }
}
