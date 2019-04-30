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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactState.LocalFileState;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.openapi.util.io.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import javax.annotation.Nullable;

/** A blaze output artifact which exists on the local file system. */
public class LocalFileOutputArtifact implements OutputArtifact {

  /**
   * Filters out non-local output files.
   *
   * <p>Some callers will only ever accept local outputs (e.g. when debugging, and making use of
   * runfiles directories).
   */
  public static ImmutableList<File> getLocalOutputFiles(Collection<OutputArtifact> outputs) {
    return outputs.stream()
        .filter(o -> o instanceof LocalFileOutputArtifact)
        .map(o -> ((LocalFileOutputArtifact) o).getFile())
        .collect(toImmutableList());
  }

  private final File file;

  public LocalFileOutputArtifact(File file) {
    this.file = file;
  }

  private long getLastModifiedTime() {
    return FileOperationProvider.getInstance().getFileModifiedTime(file);
  }

  @Override
  public long getLength() {
    return FileOperationProvider.getInstance().getFileSize(file);
  }

  @Override
  public String getKey() {
    return file.getPath();
  }

  @Override
  @Nullable
  public ArtifactState toArtifactState() {
    long lastModifiedTime = getLastModifiedTime();
    return lastModifiedTime == 0 ? null : new LocalFileState(getKey(), lastModifiedTime);
  }

  @Override
  public String getBlazeConfigurationMnemonic(BlazeConfigurationHandler handler) {
    return handler.getConfigurationMnemonic(file);
  }

  @Override
  @MustBeClosed
  public BufferedInputStream getInputStream() throws IOException {
    return new BufferedInputStream(new FileInputStream(file));
  }

  @VisibleForTesting
  public File getFile() {
    return file;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LocalFileOutputArtifact)) {
      return false;
    }
    return FileUtil.filesEqual(file, ((LocalFileOutputArtifact) obj).file);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(file);
  }
}
