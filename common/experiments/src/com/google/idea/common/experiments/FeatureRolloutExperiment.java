/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;

/**
 * An experiment controlling gradual rollout of a feature. The experiment value must be an integer
 * between 0 and 100, indicating the percentage of users for whom the feature should be active.
 *
 * <p>If no experiment value is found, it will always default to disabled.
 */
public class FeatureRolloutExperiment extends Experiment {

  public FeatureRolloutExperiment(String key) {
    super(key);
  }

  /** Returns true if the feature should be enabled for this user. */
  public boolean isEnabled() {
    if (InternalDevFlag.isInternalDev()) {
      return true;
    }
    return getUserHash(ExperimentUsernameProvider.getUsername()) < getRolloutPercentage();
  }

  /**
   * Returns an integer between 0 and 100, inclusive, indicating the percentage of users for whom
   * this feature should be enabled.
   *
   * <p>If the experiment value is outside the range [0, 100], 0 is returned.
   */
  private int getRolloutPercentage() {
    int percentage =
        ExperimentService.getInstance().getExperimentInt(getKey(), /* defaultValue= */ 0);
    return percentage < 0 || percentage > 100 ? 0 : percentage;
  }

  /**
   * Returns an integer between 0 and 99, inclusive, based on a hash of the feature key and
   * username. If the rollout percentage is greater than this value, the feature will be enabled for
   * this user.
   *
   * <p>If {@code userName} is null, returns 99 (meaning the feature will be inactive, unless it's
   * set to 100% rollout).
   */
  @VisibleForTesting
  int getUserHash(@Nullable String userName) {
    if (userName == null) {
      return 99;
    }
    int hash = (userName + getKey()).hashCode();
    return Math.abs(hash) % 100;
  }
}
