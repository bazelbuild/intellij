/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import javax.annotation.Nullable;

/** Output artifacts from the BEP. */
public interface OutputArtifacts {
  static OutputArtifacts fromProjectData(@Nullable BlazeProjectData projectData) {
    return fromTargetData(projectData != null ? projectData.getTargetData() : null);
  }

  static OutputArtifacts fromTargetData(@Nullable ProjectTargetData targetData) {
    return TrackedOutputArtifacts.enable.getValue()
        ? targetData != null ? targetData.trackedOutputs : TrackedOutputArtifacts.EMPTY
        : targetData != null ? targetData.remoteOutputs : RemoteOutputArtifacts.EMPTY;
  }

  boolean hasRemoteOutputs();

  OutputArtifact resolveGenfilesPath(String genfilesRelativePath);

  OutputArtifact findOutputArtifact(ArtifactLocation location);

  OutputArtifact findOutputArtifact(String blazeOutRelativePath);
}
