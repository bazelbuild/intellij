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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.util.Objects;
import java.util.Set;

/** Data class to (de)serialize a single entry in cache data */
public final class CacheEntry {
  private final String cacheKey;

  private final String fileName;
  private final Set<Metadata> artifacts;

  public CacheEntry(String cacheKey, String fileName, Set<Metadata> artifacts) {
    this.cacheKey = cacheKey;
    this.fileName = fileName;
    this.artifacts = artifacts;
  }

  /**
   * {@code cacheKey} should be the same value for a specific set of {@link BlazeArtifact}
   * regardless of how or when they were generated.
   */
  public String getCacheKey() {
    return cacheKey;
  }

  public String getFileName() {
    return fileName;
  }

  public ImmutableSet<Metadata> getArtifacts() {
    return ImmutableSet.copyOf(artifacts);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof CacheEntry)) {
      return false;
    }

    CacheEntry other = (CacheEntry) o;
    return Objects.equals(cacheKey, other.cacheKey)
        && Objects.equals(fileName, other.fileName)
        && this.artifacts.containsAll(other.artifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cacheKey, fileName, artifacts);
  }

  /** Returns a {@link CacheEntry} corresponding to the given {@code OutputArtifact}. */
  @VisibleForTesting
  public static CacheEntry forArtifact(OutputArtifact blazeArtifact)
      throws ArtifactNotFoundException {
    Metadata artifactMetadata = Metadata.forArtifact(blazeArtifact);

    String artifactPath = artifactMetadata.getRelativePath();

    String artifactNameAndExt = PathUtil.getFileName(artifactPath);
    String artifactName = FileUtil.getNameWithoutExtension(artifactNameAndExt);
    String artifactExtension = FileUtil.getExtension(artifactNameAndExt, "").toString();

    // Logic for generating cache keys (matches the current FileCache logic)
    String cacheKey = getCachedKey(artifactName, artifactPath);

    String localFileName = cacheKey;
    if (!artifactExtension.isEmpty()) {
      localFileName += ("." + artifactExtension);
    }

    return new CacheEntry(cacheKey, localFileName, ImmutableSet.of(artifactMetadata));
  }

  public static String getCachedKey(String name, String relativePath) {
    return name + "_" + Integer.toHexString(relativePath.hashCode());
  }

  /** Returns a {@link CacheEntry} corresponding to the given {@code File}. */
  @VisibleForTesting
  public static CacheEntry forFile(File file) {
    Metadata metadata = Metadata.forFile(file);
    String relativePath = metadata.getRelativePath();

    String nameAndExt = PathUtil.getFileName(relativePath);
    String name = FileUtil.getNameWithoutExtension(nameAndExt);

    // Logic for generating cache keys (matches the current FileCache logic)
    String cacheKey = getCachedKey(name, relativePath);

    return new CacheEntry(cacheKey, file.getName(), ImmutableSet.of(metadata));
  }
}
