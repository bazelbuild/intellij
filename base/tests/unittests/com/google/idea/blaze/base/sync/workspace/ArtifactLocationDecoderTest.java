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

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ArtifactLocationDecoder}. */
@RunWith(JUnit4.class)
public class ArtifactLocationDecoderTest extends BlazeTestCase {

  private static final String EXECUTION_ROOT = "/path/to/_blaze_user/1234bf129e/root";
  private static final String OUTPUT_BASE = "/path/to/_blaze_user/1234bf129e";

  @Test
  public void testGeneratedArtifact() throws Exception {
    ArtifactLocation artifactLocation =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment("/blaze-out/bin")
            .setRelativePath("com/google/Bla.java")
            .setIsSource(false)
            .build();

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            new BlazeRoots(
                new File(EXECUTION_ROOT),
                new ExecutionRootPath("root/blaze-out/crosstool/bin"),
                new ExecutionRootPath("root/blaze-out/crosstool/genfiles"),
                new File(OUTPUT_BASE)),
            null);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(EXECUTION_ROOT + "/blaze-out/bin/com/google/Bla.java");
  }

  @Test
  public void testExternalArtifact() throws Exception {
    ArtifactLocation artifactLocation =
        ArtifactLocation.builder()
            .setRelativePath("external/com/google/Bla.java")
            .setIsSource(true)
            .setIsExternal(true)
            .build();

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            new BlazeRoots(
                new File(EXECUTION_ROOT),
                new ExecutionRootPath("root/blaze-out/crosstool/bin"),
                new ExecutionRootPath("root/blaze-out/crosstool/genfiles"),
                new File(OUTPUT_BASE)),
            null);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(OUTPUT_BASE + "/external/com/google/Bla.java");
  }
}
