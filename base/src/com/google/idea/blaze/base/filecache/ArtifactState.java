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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFile;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/** Used to diff blaze {@link OutputArtifact}s from different builds. */
public interface ArtifactState {

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

  static ImmutableMap<String, ArtifactState> convertOldFormat(Map<String, Long> fileState) {
    ImmutableMap.Builder<String, ArtifactState> map = ImmutableMap.builder();
    fileState.forEach((k, v) -> map.put(k, new LocalFileState(k, v)));
    return map.build();
  }

  @Nullable
  static ArtifactState fromProto(LocalFileOrOutputArtifact proto) {
    if (proto.hasLocalFile()) {
      return new LocalFileState(
          proto.getLocalFile().getPath(), proto.getLocalFile().getTimestamp());
    }
    if (proto.hasArtifact()) {
      ProjectData.OutputArtifact output = proto.getArtifact();
      return new GenericOutputState(
          output.getRelativePath(), output.getId(), output.getSyncStartTimeMillis());
    }
    return null;
  }

  /** Serialization state related to local files. */
  class LocalFileState implements ArtifactState {
    private final String absolutePath;
    private final long timestamp;

    public LocalFileState(String absolutePath, long timestamp) {
      this.absolutePath = absolutePath;
      this.timestamp = timestamp;
    }

    @Override
    public String getKey() {
      return absolutePath;
    }

    @Override
    public boolean isMoreRecent(ArtifactState output) {
      return !(output instanceof LocalFileState) || timestamp < ((LocalFileState) output).timestamp;
    }

    @Override
    public LocalFileOrOutputArtifact serializeToProto() {
      return LocalFileOrOutputArtifact.newBuilder()
          .setLocalFile(LocalFile.newBuilder().setPath(absolutePath).setTimestamp(timestamp))
          .build();
    }

    @Override
    public int hashCode() {
      return absolutePath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof LocalFileState)) {
        return false;
      }
      return absolutePath.equals(((LocalFileState) obj).absolutePath);
    }
  }

  /**
   * Serialization state related to arbitrary output artifacts, possible provided by a remote
   * service.
   */
  class GenericOutputState implements ArtifactState {
    private final String blazeOutPath;
    private final String id;
    private final long syncStartTimeMillis;

    public GenericOutputState(String blazeOutPath, String id, long syncStartTimeMillis) {
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
      if (!(output instanceof GenericOutputState)) {
        return true;
      }
      GenericOutputState state = (GenericOutputState) output;
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
      if (!(obj instanceof GenericOutputState)) {
        return false;
      }
      return blazeOutPath.equals(((GenericOutputState) obj).blazeOutPath);
    }
  }
}
