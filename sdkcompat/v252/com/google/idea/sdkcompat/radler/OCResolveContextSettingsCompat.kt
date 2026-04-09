/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.radler

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.settings.OCResolveContextSettings
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration

// #api252
object OCResolveContextSettingsCompat {
  fun findPriorityConfiguration(project: Project, configs: Collection<OCResolveConfiguration>): OCResolveConfiguration? {
    return OCResolveContextSettings.getInstance(project).findPriorityConfiguration(configs)?.first
  }

  fun setSelectedConfiguration(project: Project, config: OCResolveConfiguration) {
    OCResolveContextSettings.getInstance(project).setSelectedConfiguration(config)
  }
}
