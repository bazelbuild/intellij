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
package com.google.idea.blaze.base.sync.workspace;

import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.io.File;

/** Resolves all artifacts to local source files. */
public class MockArtifactLocationDecoder implements ArtifactLocationDecoder {

  @Override
  public File decode(ArtifactLocation artifactLocation) {
    return new File(artifactLocation.getRelativePath());
  }

  @Override
  public File resolveSource(ArtifactLocation artifact) {
    return decode(artifact);
  }

  @Override
  public BlazeArtifact resolveOutput(ArtifactLocation artifact) {
    if (artifact.isSource()) {
      return new SourceArtifact(decode(artifact));
    } else {
      return new LocalFileOutputArtifact(
          decode(artifact), artifact.getRelativePath(), artifact.getExecutionRootRelativePath());
    }
  }
}
