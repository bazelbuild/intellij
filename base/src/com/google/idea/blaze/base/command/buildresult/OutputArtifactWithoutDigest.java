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
package com.google.idea.blaze.base.command.buildresult;

import com.google.idea.blaze.base.filecache.ArtifactState;
import javax.annotation.Nullable;

/** A blaze output artifact, generated during some build action. */
public interface OutputArtifactWithoutDigest extends BlazeArtifact, OutputArtifactInfo {

  /** The path component related to the build configuration. */
  String getConfigurationMnemonic();

  /**
   * A key uniquely identifying an artifact between builds. Different versions of an artifact
   * produced from different blaze builds will have the same key.
   *
   * <p>TODO(brendandouglas): remove this in favor of ArtifactState#getKey
   */
  default String getKey() {
    return getRelativePath();
  }

  /**
   * Returns the {@link ArtifactState} for this output, used for serialization/diffing purposes. Can
   * require file system operations.
   */
  @Nullable
  ArtifactState toArtifactState();
}
