/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.artifacts;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * An artifact produced by a build.
 *
 * <p>This includes the digest of the artifact as indicated by bazel, it's output path and the
 * target that produced it.
 *
 * <p>Note, for the old {@link com.google.idea.blaze.base.qsync.cache.ArtifactTrackerImpl} codepaths
 * the digest will not be populated.
 */
@AutoValue
public abstract class BuildArtifact {

  /**
   * Special case to support legacy codepaths that don't use digests in here
   *
   * <p>TODO(b/323346056) remove this when the old codepaths are deleted.
   */
  public static final Function<Path, String> NO_DIGESTS = p -> "";

  public abstract String digest();

  public abstract Path path();

  public abstract Label target();

  public static BuildArtifact create(String digest, Path path, Label target) {
    return new AutoValue_BuildArtifact(digest, path, target);
  }

  public static BuildArtifact create(Path path, Label target, Function<Path, String> digestMap) {
    String digest =
        Preconditions.checkNotNull(digestMap.apply(path), "No digest for %s from %s", path, target);
    if (digestMap != NO_DIGESTS) {
      Preconditions.checkState(!digest.isEmpty(), "Empty digest for %s from %s", path, target);
    }
    return create(digest, path, target);
  }

  public ByteSource blockingGetFrom(BuildArtifactCache cache) throws BuildException {
    try {
      return Uninterruptibles.getUninterruptibly(
          cache
              .get(digest())
              .orElseThrow(() -> new BuildException("Artifact %s missing from the cache" + this)));
    } catch (ExecutionException e) {
      throw new BuildException("Failed to get artifact " + this, e);
    }
  }

  public String getExtension() {
    String fileName = path().getFileName().toString();
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot == -1) {
      return "";
    }
    return fileName.substring(lastDot + 1);
  }
}
