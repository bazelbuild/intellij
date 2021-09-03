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

import java.util.Map;

@Deprecated
final class WebExperimentLoader implements ExperimentLoader {

  private static final String DEFAULT_PLUGIN_NAME = "ijwb";

  private final WebExperimentSyncer syncer;

  WebExperimentLoader() {
    this(DEFAULT_PLUGIN_NAME);
  }

  WebExperimentLoader(String pluginName) {
    syncer = new WebExperimentSyncer(pluginName);
  }

  @Override
  public Map<String, String> getExperiments() {
    return syncer.getExperimentValues();
  }

  @Override
  public void initialize() {

    syncer.initialize();
  }
}
