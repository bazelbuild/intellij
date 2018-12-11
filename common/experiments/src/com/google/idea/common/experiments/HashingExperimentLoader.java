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

import static com.google.idea.common.experiments.ExperimentsUtil.hashExperimentName;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An experiment loader that handles hashing the experiment names, for sources that store the data
 * unhashed.
 */
abstract class HashingExperimentLoader implements ExperimentLoader {

  private volatile ImmutableMap<String, String> previousUnhashedExperiments = null;
  private volatile Map<String, String> hashedExperiments = null;

  @Override
  public Map<String, String> getExperiments() {
    ImmutableMap<String, String> unhashedExperiments = getUnhashedExperiments();
    // use object equality and avoid all locking beyond that associated with 'volatile' -- we don't
    // care if multiple threads run this calculation initially, as long as all subsequent calls
    // don't have to
    if (previousUnhashedExperiments == unhashedExperiments) {
      return hashedExperiments;
    }
    previousUnhashedExperiments = unhashedExperiments;
    hashedExperiments = hashExperiments(unhashedExperiments);
    return hashedExperiments;
  }

  private static Map<String, String> hashExperiments(Map<String, String> unhashed) {
    return unhashed.entrySet().stream()
        .collect(Collectors.toMap(e -> hashExperimentName(e.getKey()), Map.Entry::getValue));
  }

  abstract ImmutableMap<String, String> getUnhashedExperiments();
}
