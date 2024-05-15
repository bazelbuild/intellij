/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.cache;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.google.idea.blaze.common.artifact.OutputArtifactInfo;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Delegates to one of a number of other cache layouts, using the first one which returns non-null
 * for each artifact, with a default fallback.
 */
@AutoValue
public abstract class DelegatingCacheLayout implements CacheLayout {

  protected abstract ImmutableList<CacheLayout> layouts();

  protected abstract CacheLayout fallback();

  public static Builder builder() {
    return new AutoValue_DelegatingCacheLayout.Builder();
  }

  @Override
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    for (CacheLayout layout : layouts()) {
      OutputArtifactDestinationAndLayout dest =
          layout.getOutputArtifactDestinationAndLayout(outputArtifact);
      if (dest != null) {
        return dest;
      }
    }
    return Preconditions.checkNotNull(
        fallback().getOutputArtifactDestinationAndLayout(outputArtifact),
        "Fallback layout must not return null from getOutputArtifactDestinationAndLayout");
  }

  @Override
  public Collection<Path> getCachePaths() {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
    layouts().forEach(l -> builder.addAll(l.getCachePaths()));
    return builder.build();
  }

  /** Builder for {@link DelegatingCacheLayout}. */
  @AutoValue.Builder
  public abstract static class Builder {

    @CanIgnoreReturnValue
    public Builder addLayout(CacheLayout value) {
      layoutsBuilder().add(value);
      return this;
    }

    protected abstract ImmutableList.Builder<CacheLayout> layoutsBuilder();

    public abstract Builder setFallback(CacheLayout value);

    public abstract DelegatingCacheLayout build();
  }
}
