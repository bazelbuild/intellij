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

import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactState.RemoteOutputState;

/** A blaze output artifact which is hosted by some remote service. */
public interface RemoteOutputArtifact extends OutputArtifact {

  @Override
  default ArtifactState toArtifactState() {
    return new RemoteOutputState(getRelativePath(), getHashId(), getSyncTimeMillis());
  }

  @Override
  default LocalFileOrOutputArtifact toProto() {
    return LocalFileOrOutputArtifact.newBuilder()
        .setArtifact(
            ProjectData.OutputArtifact.newBuilder()
                .setRelativePath(getRelativePath())
                .setId(getHashId())
                .setSyncStartTimeMillis(getSyncTimeMillis()))
        .build();
  }

  /** Reads the artifact contents into memory, using a soft reference. */
  void prefetch();

  /**
   * A string uniquely identifying this artifact. Instances of this artifact with different contents
   * will have different IDs.
   *
   * <p>May be used to retrieve it from a remote caching service.
   */
  String getHashId();

  long getSyncTimeMillis();
}
