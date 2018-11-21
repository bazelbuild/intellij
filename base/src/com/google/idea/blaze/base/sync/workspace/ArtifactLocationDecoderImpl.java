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

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;

/** Decodes intellij_ide_info.proto ArtifactLocation file paths */
public final class ArtifactLocationDecoderImpl implements ArtifactLocationDecoder {
  private static final long serialVersionUID = 1L;

  private final BlazeInfo blazeInfo;
  private final WorkspacePathResolver pathResolver;

  public ArtifactLocationDecoderImpl(BlazeInfo blazeInfo, WorkspacePathResolver pathResolver) {
    this.blazeInfo = blazeInfo;
    this.pathResolver = pathResolver;
  }

  @Override
  public File decode(ArtifactLocation artifactLocation) {
    if (artifactLocation.isMainWorkspaceSourceArtifact()) {
      return pathResolver.resolveToFile(artifactLocation.getRelativePath());
    }
    String path =
        Paths.get(
                blazeInfo.getExecutionRoot().getPath(),
                artifactLocation.getExecutionRootRelativePath())
            .toString();
    // doesn't require file-system operations -- no attempt to resolve symlinks.
    return new File(FileUtil.toCanonicalPath(path));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactLocationDecoderImpl that = (ArtifactLocationDecoderImpl) o;
    return Objects.equals(blazeInfo, that.blazeInfo)
        && Objects.equals(pathResolver, that.pathResolver);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blazeInfo, pathResolver);
  }
}
