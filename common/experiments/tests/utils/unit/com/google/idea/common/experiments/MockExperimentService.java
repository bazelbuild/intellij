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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Used for tests. */
public class MockExperimentService implements ExperimentService {

  private Map<String, Object> experiments = new HashMap<>();

  @Override
  public void startExperimentScope() {}

  @Override
  public void endExperimentScope() {}

  public void setExperimentRaw(String key, Object value) {
    experiments.put(key, value);
  }

  public void setExperiment(BoolExperiment experiment, boolean value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  public boolean getExperiment(String key, boolean defaultValue) {
    if (experiments.containsKey(key)) {
      return (Boolean) experiments.get(key);
    }
    return defaultValue;
  }

  public void setExperimentString(StringExperiment experiment, String value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  @Nullable
  public String getExperimentString(String key, @Nullable String defaultValue) {
    if (experiments.containsKey(key)) {
      return experiments.get(key).toString();
    }
    return defaultValue;
  }

  public void setFeatureRolloutExperiment(FeatureRolloutExperiment experiment, int percentage) {
    experiments.put(experiment.getKey(), percentage);
  }

  public void setExperimentInt(IntExperiment experiment, int value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  public int getExperimentInt(String key, int defaultValue) {
    if (experiments.containsKey(key)) {
      return (Integer) experiments.get(key);
    }
    return defaultValue;
  }
}
