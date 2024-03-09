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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.artifact.TestOutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class NewArtifactTrackerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder cacheDir = new TemporaryFolder();

  @Mock BuildArtifactCache cache;
  @Captor ArgumentCaptor<ImmutableCollection<OutputArtifact>> cachedArtifactsCaptor;

  private NewArtifactTracker<NoopContext> artifactTracker;

  @Before
  public void createArtifactTracker() {
    artifactTracker = new NewArtifactTracker<>(cacheDir.getRoot().toPath(), cache);
  }

  @Test
  public void library_jars() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setRelativePath("out/test.jar")
                            .setDigest("jar_digest")
                            .build(),
                        TestOutputArtifact.builder()
                            .setRelativePath("out/anothertest.jar")
                            .setDigest("anotherjar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars("out/test.jar")
                            .build())
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:anothertest")
                            .addJars("out/anothertest.jar")
                            .build())
                    .build())
            .build(),
        new NoopContext());
    assertThat(cachedArtifactsCaptor.getValue().stream().map(OutputArtifact::getDigest))
        .containsExactly("jar_digest", "anotherjar_digest");
    assertThat(artifactTracker.getLiveCachedTargets())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(builtDeps).hasSize(2);
    ImmutableMap<Label, JavaArtifactInfo> depsMap =
        builtDeps.stream()
            .map(TargetBuildInfo::javaInfo)
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(JavaArtifactInfo::label, Functions.identity()));
    assertThat(depsMap.keySet())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    assertThat(depsMap.get(Label.of("//test:test")).jars())
        .containsExactly(
            BuildArtifact.create("jar_digest", Path.of("out/test.jar"), Label.of("//test:test")));
  }
}
