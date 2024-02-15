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
package com.google.idea.blaze.common.artifact;

import java.io.File;

/** Used to diff blaze {@link BlazeArtifact}s from different builds. */
public interface ArtifactState {

  /**
   * Migrates from a possibly-old {@link ArtifactState} key format to the latest format, which is
   * consistent for local and remote output artifacts.
   */
  static String migrateOldKeyFormat(String key) {
    // TODO(brendandouglas): remove this temporary migration code in November 2019
    if (!key.startsWith(File.separator)) {
      return key;
    }
    // try to parse the blaze-out-relative path
    String pathComponent = String.format("%1$sblaze-out%1$s", File.separator);
    int ix = key.indexOf(pathComponent);
    if (ix == -1) {
      pathComponent = String.format("%1$sbazel-out%1$s", File.separator);
      ix = key.indexOf(pathComponent);
    }
    if (ix == -1) {
      // fall back to returning the original key
      return key;
    }
    return key.substring(ix + pathComponent.length());
  }

  /**
   * A key uniquely identifying an artifact between builds. Different versions of an artifact
   * produced from different blaze builds will have the same key.
   */
  String getKey();

  /**
   * Returns true if the provided {@link ArtifactState} is a more recent instance of this one. The
   * result is only valid for artifacts with the same key.
   */
  boolean isMoreRecent(ArtifactState output);


}
