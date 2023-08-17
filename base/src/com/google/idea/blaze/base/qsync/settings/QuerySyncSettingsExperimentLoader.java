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
package com.google.idea.blaze.base.qsync.settings;

import com.google.common.collect.ImmutableMap;
import com.google.idea.common.experiments.ExperimentLoader;
import java.util.Map;

/** Provides experiment value related to query sync settings. */
public class QuerySyncSettingsExperimentLoader implements ExperimentLoader {
  public static final String ID = "query-sync.settings";

  @Override
  public Map<String, String> getExperiments() {
    return ImmutableMap.of(
        "use.query.sync",
        QuerySyncSettings.getInstance().useQuerySync() ? "1" : "0",
        "query.sync.before.build",
        QuerySyncSettings.getInstance().syncBeforeBuild() ? "1" : "0");
  }

  @Override
  public void initialize() {}

  @Override
  public String getId() {
    return ID;
  }
}
