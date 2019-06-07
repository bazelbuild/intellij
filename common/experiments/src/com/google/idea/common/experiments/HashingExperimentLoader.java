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
package com.google.idea.common.experiments;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.idea.common.experiments.ExperimentNameHashes.hashExperimentName;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * An experiment loader that handles hashing the experiment names, for sources that store the data
 * unhashed.
 */
abstract class HashingExperimentLoader implements ExperimentLoader {

  private static final class ExperimentCache {
    private final ImmutableMap<String, String> previousUnhashedExperiments;
    private final ImmutableMap<String, String> hashedExperiments;

    private ExperimentCache(
        ImmutableMap<String, String> previousUnhashedExperiments,
        ImmutableMap<String, String> hashedExperiments) {
      this.previousUnhashedExperiments = previousUnhashedExperiments;
      this.hashedExperiments = hashedExperiments;
    }
  }

  @Nullable private volatile ExperimentCache experimentCache;

  @Override
  public Map<String, String> getExperiments() {
    ImmutableMap<String, String> unhashedExperiments = getUnhashedExperiments();
    ExperimentCache cache = experimentCache;
    if (cache != null && cache.previousUnhashedExperiments.equals(unhashedExperiments)) {
      return cache.hashedExperiments;
    }
    // No synchronization besides 'volatile': we want callers to use the cache when experiments
    // haven't changed, but don't care if multiple threads compute hashes when they do change.
    ImmutableMap<String, String> hashedExperiments = hashExperiments(unhashedExperiments);
    experimentCache = new ExperimentCache(unhashedExperiments, hashedExperiments);
    return hashedExperiments;
  }

  private static ImmutableMap<String, String> hashExperiments(Map<String, String> unhashed) {
    return unhashed.entrySet().stream()
        .collect(toImmutableMap(e -> hashExperimentName(e.getKey()), Map.Entry::getValue));
  }

  abstract ImmutableMap<String, String> getUnhashedExperiments();
}
