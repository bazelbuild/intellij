/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.FeatureRolloutExperiment;

/** Holder class for basic information about querysync, e.g. is it enabled? */
public class QuerySync {

  public static final String BUILD_DEPENDENCIES_ACTION_NAME = "Enable analysis";

  private static final FeatureRolloutExperiment ENABLED =
      new FeatureRolloutExperiment("query.sync");

  public static final BoolExperiment ATTACH_DEP_SRCJARS =
      new BoolExperiment("querysync.attach.dep.srcjars", true);

  /**
   * Previously, query sync was enabled by an experiment. Some users still have that experiment set
   * and we don't want to inadvertently disable query sync for them.
   *
   * <p>TODO(b/303698519) Remove this workaround once query sync is enabled by default.
   */
  private static final BoolExperiment LEGACY_EXPERIMENT =
      new BoolExperiment("use.query.sync", false);

  private QuerySync() {}

  public static boolean useByDefault() {
    return ENABLED.isEnabled();
  }

  public static boolean isLegacyExperimentEnabled() {
    return LEGACY_EXPERIMENT.getValue();
  }

  /**
   * Checks if query sync for new project is enabled via experiment or settings page.
   */
  public static boolean useForNewProjects() {
    if (useByDefault()) {
      return QuerySyncSettings.getInstance().useQuerySync();
    }
    else {
      return isLegacyExperimentEnabled() || QuerySyncSettings.getInstance().useQuerySyncBeta();
    }
  }
}
