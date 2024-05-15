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
package com.google.idea.blaze.base.qsync.cache;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import com.google.idea.blaze.common.artifact.OutputArtifactInfo;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeyBasedCacheLayoutTest {

  @Rule public CacheDirectories cacheDirectories = new CacheDirectories();

  @Test
  public void basic_artifact() {
    KeyBasedCacheLayout cacheLayout = new KeyBasedCacheLayout(cacheDirectories.cacheDirectory());

    OutputArtifactInfo outputArtifact = TestOutputArtifactInfo.create("libfoo.jar");

    OutputArtifactDestination artifactDestination =
        cacheLayout.getOutputArtifactDestinationAndLayout(outputArtifact);
    Path relativeCopyDestination =
        cacheDirectories.cacheDirectory().relativize(artifactDestination.getCopyDestination());

    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    assertThat(relativeCopyDestination.toString()).isEqualTo(cacheKey);
  }


}
