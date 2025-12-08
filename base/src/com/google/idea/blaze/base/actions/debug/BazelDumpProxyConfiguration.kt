/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.buildview.BazelProxyHelper
import com.google.idea.blaze.base.model.BlazeProjectData
import com.intellij.openapi.project.Project

class BazelDumpProxyConfiguration : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val builder = StringBuilder()

    builder.appendLine("FROM ENVIRONMENT:")
    builder.appendLine("  HTTP_PROXY=${System.getenv("HTTP_PROXY")}")
    builder.appendLine("  HTTPS_PROXY=${System.getenv("HTTPS_PROXY")}")
    builder.appendLine("  NO_PROXY=${System.getenv("NO_PROXY")}")

    val config = BazelProxyHelper.getConfiguration()

    builder.appendLine("LOCAL CONFIGURATION:")
    config.forEach { (key, value) -> builder.appendLine("  $key=$value") }

    return builder.toString()
  }
}