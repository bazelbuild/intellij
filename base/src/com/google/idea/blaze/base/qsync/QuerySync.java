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

import com.google.common.base.Suppliers;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.function.Supplier;

/** Holder class for basic information about querysync, e.g. is it enabled? */
public class QuerySync {

  private static final Logger logger = Logger.getInstance(QuerySync.class);

  // Only read the initial value, as the sync mode should not change over a single run of the IDE.
  public static final Supplier<Boolean> BETA_ENABLED =
      Suppliers.memoize(new BoolExperiment("use.query.sync.beta", false)::getValue);

  /** Enable compose preview for Query Sync. */
  private static final Supplier<Boolean> COMPOSE_ENABLED =
      Suppliers.memoize(new BoolExperiment("aswb.query.sync.enable.compose", false)::getValue);

  public static final BoolExperiment CC_SUPPORT_ENABLED =
      new BoolExperiment("querysync.cc.support", false);

  private QuerySync() {}

  public static boolean isEnabled() {
    return QuerySyncSettings.getInstance().useQuerySync();
  }

  /**
   * Returns whether query sync is enabled for this project. Caller needs to make sure {@link
   * BlazeImportSettings} has been loaded before calling it. Otherwise, caller may receive
   * unexpected NPE. e.g. {@link BlazeImportSettings} has not been loaded when initializing an
   * application service.
   *
   * @deprecated use {@link Blaze#getProjectType}.
   */
  @Deprecated
  public static boolean isEnabled(Project project) {
    return BlazeImportSettingsManager.getInstance(project)
        .getImportSettings()
        .getProjectType()
        .equals(ProjectType.QUERY_SYNC);
  }

  public static boolean isComposeEnabled() {
    return isEnabled() && COMPOSE_ENABLED.get();
  }

  public static void assertNotEnabled(String reason) {
    if (isEnabled()) {
      NotSupportedWithQuerySyncException e = new NotSupportedWithQuerySyncException(reason);
      // make sure the exception doesn't get silently swallowed later:
      logger.error(e);
      throw e;
    }
  }
}
