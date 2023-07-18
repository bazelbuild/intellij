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
package com.google.idea.blaze.base.sync.workspace;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.devtools.intellij.aspect.Common;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ArtifactLocationDecoder}. */
@RunWith(JUnit4.class)
public class ArtifactLocationDecoderTest extends BlazeTestCase {

  private static final String OUTPUT_BASE = "/path/to/_blaze_user/1234bf129e";
  private static final String EXECUTION_ROOT = OUTPUT_BASE + "/execroot/my_proj";

  @Test
  public void testGeneratedArtifact() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment("/blaze-out/bin")
            .setRelativePath("com/google/Bla.java")
            .setIsSource(false)
            .build();

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(EXECUTION_ROOT + "/blaze-out/bin/com/google/Bla.java");
  }

  @Test
  public void testExternalSourceArtifact() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("com/google/Bla.java")
                .setRootExecutionPathFragment("../repo_name")
                .setIsSource(true)
                .setIsExternal(true)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("../repo_name/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(OUTPUT_BASE + "/execroot/repo_name/com/google/Bla.java");
  }

  @Test
  public void testExternalDerivedArtifact() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("com/google/Bla.java")
                .setRootExecutionPathFragment("../repo_name/blaze-out/crosstool/bin")
                .setIsSource(false)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("../repo_name/blaze-out/crosstool/bin/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(OUTPUT_BASE + "/execroot/repo_name/blaze-out/crosstool/bin/com/google/Bla.java");
  }

  @Test
  public void testResolveSourceToProjectWorkspace() throws IOException {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("something.h")
                .setRootExecutionPathFragment("external/repo_name")
                .setIsSource(true)
                .setIsExternal(true)
                .build());

    BlazeInfo blazeInfo = mock(BlazeInfo.class, RETURNS_DEEP_STUBS);

    File workspaceRootFile = new File("/workspace/root");
    WorkspacePathResolver resolver =
        new WorkspacePathResolverImpl(new WorkspaceRoot(workspaceRootFile));

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            blazeInfo,
            resolver,
            RemoteOutputArtifacts.EMPTY);

    when(blazeInfo.getExecutionRoot().toPath()
        .resolve(artifactLocation.getExecutionRootRelativePath()).toRealPath()).thenReturn(
        workspaceRootFile.toPath().resolve("something.h"));

    assertThat(decoder.resolveSource(artifactLocation))
        .isEqualTo(workspaceRootFile.toPath().resolve("something.h").toFile());
  }
}
