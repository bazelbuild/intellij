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
package com.google.idea.blaze.clwb.run

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.SyncListener
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.SyncResult
import com.google.idea.common.aquery.ActionGraph
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.jetbrains.cidr.lang.settings.OCResolveContextSettings

/**
 * Stores the per-target Bazel configuration checksums from the last aquery run.
 *
 * Different targets contributing to the same binary can have different Bazel configurations
 * (due to transitions). This state allows the resolve configuration chooser to match
 * OCResolveConfigurations to the configurations actually used in the last build.
 *
 * Session-only state (not persisted). Cleared on sync since configurations may change.
 */
@Service(Service.Level.PROJECT)
class LastBuildConfigurations(private val project: Project) {

  /** The set of unique configuration checksums from the last build. */
  @Volatile
  var preferredConfigurations: Set<String> = emptySet()
    private set

  fun update(compileActions: Map<Label, ActionGraph.Action>) {
    preferredConfigurations = compileActions.values.map { it.configuration.checksum }.toSet()
    project.messageBus.syncPublisher(TOPIC).preferredConfigurationChanged()
  }

  private fun clear() {
    preferredConfigurations = emptySet()
    project.messageBus.syncPublisher(TOPIC).preferredConfigurationChanged()
  }

  companion object {

    @JvmField
    val TOPIC: Topic<Listener> = Topic.create("LastBuildConfigurations", Listener::class.java)

    @JvmStatic
    fun getInstance(project: Project): LastBuildConfigurations =
      project.service<LastBuildConfigurations>()
  }

  /** Clears stored build configurations on sync since configurations may change. */
  class SyncCleaner : SyncListener {

    override fun afterSync(
      project: Project,
      context: BlazeContext,
      syncMode: SyncMode,
      syncResult: SyncResult,
      buildIds: ImmutableSet<Int>,
    ) = getInstance(project).clear()
  }

  /** Notifies the OCResolveContext system when preferred configurations change. */
  class OCResolveContextNotifier(private val project: Project) : Listener {

    override fun preferredConfigurationChanged() {
      OCResolveContextSettings.getInstance(project).notifyPrioritiesChange()
    }
  }

  /** Callback for listening for configuration changes. */
  interface Listener {

    fun preferredConfigurationChanged()
  }
}
