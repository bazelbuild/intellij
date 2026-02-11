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
package com.google.idea.blaze.base.actions.debug

import com.google.idea.blaze.base.model.BlazeProjectData
import com.intellij.openapi.project.Project

class BazelDumpConfigurationData : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val builder = StringBuilder()

    data.configurationData().configurations.entries.sortedBy { it.key }.forEach { (configId, config) ->
      builder.appendLine("Configuration ID: $configId")
      builder.appendLine("-> mnemonic: ${config.mnemonic()}")
      builder.appendLine("-> platform: ${config.platformName()}")
      builder.appendLine("->      cpu: ${config.cpu()}")
      builder.appendLine("->  is tool: ${config.isToolConfiguration()}")

      val vars = config.makeVariables()
      builder.appendLine("Make Variables (${vars.size}):")
      vars.entries.sortedBy { it.key }.forEach { (key, value) ->
        builder.appendLine("-> $key = $value")
      }

      builder.appendLine("")
    }

    return builder.toString()
  }
}
