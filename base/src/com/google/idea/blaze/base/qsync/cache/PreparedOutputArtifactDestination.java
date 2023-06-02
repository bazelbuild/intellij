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

import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.io.IOException;
import java.nio.file.Path;

/** A record that describes the location of an output artifact in cache directories. */
public class PreparedOutputArtifactDestination implements OutputArtifactDestinationAndLayout {

  private final String key;
  /**
   * The location where in the cache directory the representation of the artifact for the IDE should
   * be placed.
   */
  public final Path finalDestination;

  public PreparedOutputArtifactDestination(String key, Path finalDestination) {
    this.key = key;
    this.finalDestination = finalDestination;
  }

  @Override
  public String getKey() {
    return key;
  }

  /**
   * The location where in the cache directory the artifact should be placed.
   *
   * <p>The final and copy destinations are the same.
   */
  @Override
  public Path getCopyDestination() {
    return finalDestination;
  }

  @Override
  public Path prepareFinalLayout() throws IOException {
    return finalDestination;
  }
}
