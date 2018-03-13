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

/**
 * An experiment controlling gradual rollout of a feature. The experiment value must be an integer
 * between 0 and 100, indicating the percentage of users for whom the feature should be active.
 *
 * <p>If no experiment value is found, it will always default to disabled.
 */
public class FeatureRolloutExperiment extends Experiment {

  private static final String INTERNAL_DEV_SYSTEM_PROPERTY = "blaze.internal.plugin.dev";

  public FeatureRolloutExperiment(String key) {
    super(key);
  }

  /** Returns true if the feature should be enabled for this user. */
  public boolean isEnabled() {
    if (isInternalDev()) {
      return true;
    }
    int rolloutPercentage = getRolloutPercentage();
    return getUserHash(getKey()) < rolloutPercentage;
  }

  /**
   * Returns an integer between 0 and 100, inclusive, indicating the percentage of users for whom
   * this feature should be enabled.
   *
   * <p>If the experiment value is outside the range [0, 100], 0 is returned.
   */
  @VisibleForTesting
  int getRolloutPercentage() {
    int percentage = ExperimentService.getInstance().getExperimentInt(getKey(), 0);
    return percentage < 0 || percentage > 100 ? 0 : percentage;
  }

  /**
   * Returns an integer between 0 and 99, inclusive, based on a hash of the 'user.name' system
   * property and the feature key. If the rollout percentage is greater than this value, the feature
   * will be enabled for this user.
   *
   * <p>If no username is found returns 99 (meaning the feature will be inactive, unless it's set to
   * 100% rollout).
   */
  @VisibleForTesting
  static int getUserHash(String key) {
    String userName = System.getProperty("user.name");
    if (userName == null) {
      return 99;
    }
    int hash = (userName + key).hashCode();
    return Math.abs(hash) % 100;
  }

  /** Set a system property marking the current user as an internal plugin dev. */
  public static void markUserAsInternalDev(boolean isInternalDev) {
    System.setProperty(INTERNAL_DEV_SYSTEM_PROPERTY, isInternalDev ? "true" : "false");
  }

  private static boolean isInternalDev() {
    return System.getProperty(INTERNAL_DEV_SYSTEM_PROPERTY, "false").equals("true");
  }
}
