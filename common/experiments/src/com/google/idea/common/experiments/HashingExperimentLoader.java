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

import java.util.Map;
import java.util.stream.Collectors;

/**
 * An experiment loader that handles hashing the experiment names, for sources that store the data
 * unhashed.
 */
abstract class HashingExperimentLoader implements ExperimentLoader {

  @Override
  public Map<String, String> getExperiments() {
    return getUnhashedExperiments()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> hashExperimentName(e.getKey()), Map.Entry::getValue));
  }

  abstract Map<String, String> getUnhashedExperiments();
}
