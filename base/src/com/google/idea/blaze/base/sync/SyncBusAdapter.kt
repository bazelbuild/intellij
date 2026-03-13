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
package com.google.idea.blaze.base.sync

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.TargetExpression
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.intellij.openapi.project.Project

/**
 * Simple adapter for forwarding sync events to the message bus.
 */
class SyncBusAdapter : SyncListener {

  override fun onSyncStart(
    project: Project,
    context: BlazeContext,
    syncMode: SyncMode,
  ) {
    project.messageBus.syncPublisher(SyncListener.TOPIC).onSyncStart(
      project,
      context,
      syncMode,
    )
  }

  override fun buildStarted(
    project: Project,
    context: BlazeContext,
    fullProjectSync: Boolean,
    buildId: Int,
    targets: ImmutableList<TargetExpression>,
  ) {
    project.messageBus.syncPublisher(SyncListener.TOPIC).buildStarted(
      project,
      context,
      fullProjectSync,
      buildId,
      targets,
    )
  }

  override fun onSyncComplete(
    project: Project,
    context: BlazeContext,
    importSettings: BlazeImportSettings,
    projectViewSet: ProjectViewSet,
    buildIds: ImmutableSet<Int>?,
    blazeProjectData: BlazeProjectData,
    syncMode: SyncMode,
    syncResult: SyncResult,
  ) {
    project.messageBus.syncPublisher(SyncListener.TOPIC).onSyncComplete(
      project,
      context,
      importSettings,
      projectViewSet,
      buildIds,
      blazeProjectData,
      syncMode,
      syncResult,
    )
  }

  override fun afterSync(
    project: Project,
    context: BlazeContext,
    syncMode: SyncMode,
    syncResult: SyncResult,
    buildIds: ImmutableSet<Int>,
  ) {
    project.messageBus.syncPublisher(SyncListener.TOPIC).afterSync(
      project,
      context,
      syncMode,
      syncResult,
      buildIds,
    )
  }
}