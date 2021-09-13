/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.filecache;

import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFile;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.io.FileOperationProvider;
import java.io.File;
import java.util.Objects;

/** Data class to (de)serialize metadata of an artifact in cache */
public final class ArtifactMetadata {
  private final String relativePath;
  // String to uniquely identify a file with a given relative path
  private final String identifier;

  public ArtifactMetadata(String relativePath, String identifier) {
    this.relativePath = relativePath;
    this.identifier = identifier;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public String getIdentifier() {
    return identifier;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof ArtifactMetadata)) {
      return false;
    }

    ArtifactMetadata other = (ArtifactMetadata) o;
    return Objects.equals(relativePath, other.relativePath)
        && Objects.equals(identifier, other.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(relativePath, identifier);
  }

  /** Converts the given {@code artifact} to serializable {@link LocalFileOrOutputArtifact} */
  public static ArtifactMetadata forArtifact(BlazeArtifact artifact) {
    if (artifact instanceof OutputArtifact) {
      return getArtifactMetadataForOutputArtifact((OutputArtifact) artifact);
    } else if (artifact instanceof SourceArtifact) {
      File artifactFile = ((SourceArtifact) artifact).getFile();
      return new ArtifactMetadata(
          artifactFile.getPath(),
          Long.toString(FileOperationProvider.getInstance().getFileModifiedTime(artifactFile)));
    }

    throw new IllegalArgumentException("Unsupported BlazeArtifact " + artifact.getClass());
  }

  private static ArtifactMetadata getArtifactMetadataForOutputArtifact(
      OutputArtifact outputArtifact) {
    ArtifactState artifactState = outputArtifact.toArtifactState();
    if (artifactState == null) {
      throw new IllegalArgumentException("Could not get ArtifactState of " + outputArtifact);
    }
    // Serialize to proto to make grabbing the fields easier
    LocalFileOrOutputArtifact serializedArtifact = artifactState.serializeToProto();
    if (serializedArtifact.hasArtifact()) {
      ProjectData.OutputArtifact o = serializedArtifact.getArtifact();
      return new ArtifactMetadata(o.getRelativePath(), o.getId());
    } else {
      LocalFile o = serializedArtifact.getLocalFile();
      String relativePath = o.getRelativePath().isEmpty() ? o.getPath() : o.getRelativePath();
      return new ArtifactMetadata(relativePath, Long.toString(o.getTimestamp()));
    }
  }
}
