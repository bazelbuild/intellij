/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.ArtifactTrackerState;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactTrackerStateSerializationTest {

  private ImmutableMap<Label, TargetBuildInfo> roundTrip(Map<Label, TargetBuildInfo> depsMap) {
    ArtifactTrackerState proto =
        new ArtifactTrackerStateSerializer().visitDepsMap(depsMap).toProto();
    ArtifactTrackerStateDeserializer deserializer = new ArtifactTrackerStateDeserializer();
    deserializer.visit(proto);
    return deserializer.getBuiltDepsMap();
  }

  @Test
  public void test_empty() {
    ImmutableMap<Label, TargetBuildInfo> depsMap = ImmutableMap.of();
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_java_info() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            "abc-def",
            Instant.ofEpochMilli(1000),
            Optional.of(new VcsState("workspaceId", "12345", ImmutableSet.of(), Optional.empty())));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forJavaTarget(
                JavaArtifactInfo.builder()
                    .setLabel(Label.of("//my/package:target"))
                    .setJars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "jardigest",
                                Path.of("/build/out/classes.jar"),
                                Label.of("//my/package:target"))))
                    .setIdeAars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "aardigest",
                                Path.of("/build/out/resources.aar"),
                                Label.of("//my/package:target"))))
                    .setGenSrcs(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "gensrcdigest",
                                Path.of("/build/out/Generated.java"),
                                Label.of("//my/package:target"))))
                    .setSources(ImmutableSet.of(Path.of("/workspace/path/Source.java")))
                    .setSrcJars(ImmutableSet.of(Path.of("/workspace/path/sources.srcjar")))
                    .setAndroidResourcesPackage("com.my.package")
                    .build(),
                buildContext));
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }
}
