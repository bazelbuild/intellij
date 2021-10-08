/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RemoteOutputsCache} */
@RunWith(JUnit4.class)
public class RemoteOutputsCacheTest {
  @Test
  public void testNormalExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo/bar/SourceFile.java");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("SourceFile_827b9898.java");
  }

  @Test
  public void testNoExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/include/vector");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("vector_4d304806");
  }

  @Test
  public void testDoubleExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo/bar/proto.pb.go");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("proto_f767ed9d.pb.go");
  }

  @Test
  public void testExtensionOnly() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo/bar/.bazelrc");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("_764d6d66.bazelrc");
  }

  @Test
  public void testEndingDot() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo/bar/foo.");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_bddfde49.");
  }

  @Test
  public void testDoubleDot() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo/bar/foo..bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_f2dbf6ee..bar");
  }

  @Test
  public void testDotInDirectory() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt/foo.bar/foo.bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_df1c67cb.bar");
  }

  @Test
  public void testWindowsStylePath() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getKey()).thenReturn("k8-opt\\foo\\bar\\foo.bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_2a410243.bar");
  }
}
