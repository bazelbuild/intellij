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
package com.google.idea.blaze.base.command.config

import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker
import com.google.idea.blaze.base.model.BlazeConfigurationData
import com.google.idea.blaze.base.scope.BlazeContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Runs `bazel config --dump_all --output=json` and parses the result into [BlazeConfigurationData]. All actions are
 * executed blocking on a background thread.
 */
interface BlazeConfigRunner {

  companion object {

    @JvmStatic
    fun getInstance(): BlazeConfigRunner = service()
  }

  /**
   * Returns the parsed result of the bazel config command.
   */
  @Throws(BlazeConfigException::class)
  fun runBlazeConfig(
    project: Project,
    invoker: BuildInvoker,
    context: BlazeContext,
  ): BlazeConfigurationData

  /**
   * Returns the raw JSON output of the bazel config command.
   */
  @Throws(BlazeConfigException::class)
  fun runBlazeConfigGetJson(
    project: Project,
    context: BlazeContext,
    invoker: BuildInvoker,
  ): String
}