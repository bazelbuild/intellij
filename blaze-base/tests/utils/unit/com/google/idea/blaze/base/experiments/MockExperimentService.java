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
package com.google.idea.blaze.base.experiments;

import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Used for tests.
 */
public class MockExperimentService implements ExperimentService {

  private Map<String, Object> experiments = Maps.newHashMap();

  @Override
  public void reloadExperiments() {
  }

  @Override
  public void startExperimentScope() {
  }

  @Override
  public void endExperimentScope() {
  }

  public void setExperiment(@NotNull BoolExperiment experiment, @NotNull boolean value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  public boolean getExperiment(@NotNull String key, boolean defaultValue) {
    if (experiments.containsKey(key)) {
      return (Boolean)experiments.get(key);
    }
    return defaultValue;
  }

  @Override
  @Nullable
  public String getExperimentString(@NotNull String key, @Nullable String defaultValue) {
    if (experiments.containsKey(key)) {
      return (String)experiments.get(key);
    }
    return defaultValue;
  }

  @Override
  public int getExperimentInt(@NotNull String key, int defaultValue) {
    if (experiments.containsKey(key)) {
      return (Integer)experiments.get(key);
    }
    return defaultValue;
  }
}
