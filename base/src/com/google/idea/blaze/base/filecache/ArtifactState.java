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
package com.google.idea.blaze.base.filecache;

import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFile;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;

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

  LocalFileOrOutputArtifact serializeToProto();

  @Nullable
  static ArtifactState fromProto(LocalFileOrOutputArtifact proto) {
    if (proto.hasLocalFile()) {
      LocalFile local = proto.getLocalFile();
      String blazeOutPath =
          !local.getRelativePath().isEmpty()
              ? local.getRelativePath()
              : migrateOldKeyFormat(local.getPath());
      return new LocalFileState(blazeOutPath, proto.getLocalFile().getTimestamp());
    }
    if (proto.hasArtifact()) {
      ProjectData.OutputArtifact output = proto.getArtifact();
      return new RemoteOutputState(
          output.getRelativePath(), output.getId(), output.getSyncStartTimeMillis());
    }
    return null;
  }

  /** Serialization state related to local files. */
  class LocalFileState implements ArtifactState {
    private final String blazeOutPath;
    private final long timestamp;

    public LocalFileState(String blazeOutPath, long timestamp) {
      this.blazeOutPath = blazeOutPath;
      this.timestamp = timestamp;
    }

    @Override
    public String getKey() {
      return blazeOutPath;
    }

    @Override
    public boolean isMoreRecent(ArtifactState output) {
      return !(output instanceof LocalFileState) || timestamp < ((LocalFileState) output).timestamp;
    }

    @Override
    public LocalFileOrOutputArtifact serializeToProto() {
      return LocalFileOrOutputArtifact.newBuilder()
          .setLocalFile(LocalFile.newBuilder().setPath(blazeOutPath).setTimestamp(timestamp))
          .build();
    }

    @Override
    public int hashCode() {
      return blazeOutPath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof LocalFileState)) {
        return false;
      }
      return blazeOutPath.equals(((LocalFileState) obj).blazeOutPath);
    }
  }

  /** Serialization state related to remotely-hosted output artifacts. */
  class RemoteOutputState implements ArtifactState {
    private final String blazeOutPath;
    private final String id;
    private final long syncStartTimeMillis;

    public RemoteOutputState(String blazeOutPath, String id, long syncStartTimeMillis) {
      this.blazeOutPath = blazeOutPath;
      this.id = id;
      this.syncStartTimeMillis = syncStartTimeMillis;
    }

    @Override
    public String getKey() {
      return blazeOutPath;
    }

    @Override
    public boolean isMoreRecent(ArtifactState output) {
      if (!(output instanceof RemoteOutputState)) {
        return true;
      }
      RemoteOutputState state = (RemoteOutputState) output;
      return !Objects.equals(id, state.id) && syncStartTimeMillis < state.syncStartTimeMillis;
    }

    @Override
    public LocalFileOrOutputArtifact serializeToProto() {
      return LocalFileOrOutputArtifact.newBuilder()
          .setArtifact(
              ProjectData.OutputArtifact.newBuilder()
                  .setRelativePath(blazeOutPath)
                  .setId(id)
                  .setSyncStartTimeMillis(syncStartTimeMillis))
          .build();
    }

    @Override
    public int hashCode() {
      return blazeOutPath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof RemoteOutputState)) {
        return false;
      }
      return blazeOutPath.equals(((RemoteOutputState) obj).blazeOutPath);
    }
  }
}
