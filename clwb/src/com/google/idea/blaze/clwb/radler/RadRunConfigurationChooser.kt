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
package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.clwb.run.LastBuildConfigurations
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.google.idea.sdkcompat.radler.OCResolveConfigurationChooserAdapter
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration

/**
 * Selects the OCResolveConfiguration matching the Bazel configuration from the last build.
 *
 * Registered after PriorityBasedChooser so that manual user selection (via the config widget)
 * takes priority. When no manual selection exists, this chooser picks the configuration
 * matching the last aquery result.
 *
 * CLion pre-filters the configurations list to those relevant to the current file,
 * so matching against the full set of build checksums correctly resolves per-file.
 */
class RadRunConfigurationChooser : OCResolveConfigurationChooserAdapter() {

  override fun doSelectResolveConfiguration(
    project: Project,
    configurations: List<OCResolveConfiguration>,
  ): OCResolveConfiguration? = byLastBuild(project, configurations) ?: byConfigurationID(configurations)

  private fun byLastBuild(
    project: Project,
    configurations: List<OCResolveConfiguration>,
  ): OCResolveConfiguration? {
    val checksums = LastBuildConfigurations.getInstance(project).preferredConfigurations
    if (checksums.isEmpty()) return null

    return configurations.firstOrNull { config ->
      val id = BlazeResolveConfigurationID.fromOCResolveConfiguration(config) ?: return@firstOrNull false
      checksums.any { checksum -> checksum.startsWith(id.configurationId) }
    }
  }

  // deterministic fallback
  private fun byConfigurationID(configurations: List<OCResolveConfiguration>): OCResolveConfiguration? {
     return configurations.maxByOrNull {
       BlazeResolveConfigurationID.fromOCResolveConfiguration(it)?.configurationId ?: ""
     }
  }
}
