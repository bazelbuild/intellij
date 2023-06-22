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
package com.google.idea.blaze.android.experiments;

import com.google.idea.common.experiments.FileExperimentLoader;
import com.intellij.util.SystemProperties;
import java.io.File;

/** Reads Studio specific experiments override values from the user home directory. */
public class StudioUserOverridesExperimentLoader extends FileExperimentLoader {

  private static final String USER_EXPERIMENT_OVERRIDES_FILE =
      SystemProperties.getUserHome() + File.separator + ".studio-experiments";

  StudioUserOverridesExperimentLoader() {
    super(USER_EXPERIMENT_OVERRIDES_FILE);
  }
}
