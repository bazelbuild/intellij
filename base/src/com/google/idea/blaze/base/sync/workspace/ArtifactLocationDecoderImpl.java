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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.io.File;

/** Decodes intellij_ide_info.proto ArtifactLocation file paths */
public class ArtifactLocationDecoderImpl implements ArtifactLocationDecoder {
  private static final long serialVersionUID = 1L;

  private final BlazeRoots blazeRoots;
  private final WorkspacePathResolver pathResolver;

  public ArtifactLocationDecoderImpl(BlazeRoots blazeRoots, WorkspacePathResolver pathResolver) {
    this.blazeRoots = blazeRoots;
    this.pathResolver = pathResolver;
  }

  @Override
  public File decode(ArtifactLocation artifactLocation) {
    if (artifactLocation.isSource) {
      if (artifactLocation.isExternal) {
        return new File(blazeRoots.externalSourceRoot, artifactLocation.relativePath);
      }
      File root = pathResolver.findPackageRoot(artifactLocation.relativePath);
      return new File(root, artifactLocation.relativePath);
    }
    return new File(blazeRoots.executionRoot, artifactLocation.getExecutionRootRelativePath());
  }
}
