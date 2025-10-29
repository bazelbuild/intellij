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
package com.google.idea.blaze.base.actions

import com.google.idea.blaze.base.projectview.ProjectViewStorageManager
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.sync.BlazeSyncManager
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

class SwitchProjectViewAction : BlazeProjectAction(), DumbAware {

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val project = e.project ?: return
    val file = getFile(e) ?: return

    // TODO: check if project view file can be parsed?

    val settings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return
    settings.projectViewFile = file.path

    // this also reloads the project view
    BlazeSyncManager.getInstance(project).fullProjectSync( /* reason= */ "SwitchProjectViewAction")
  }

  override fun querySyncSupport(): QuerySyncStatus? = QuerySyncStatus.DISABLED

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabled(e)
  }

  private fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    if (BlazeSyncStatus.getInstance(project).syncInProgress()) {
      return false
    }

    val file = getFile(e) ?: return false
    if (!ProjectViewStorageManager.isProjectViewFile(file.name)) {
      return false
    }

    val settings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return false
    return file.path != settings.projectViewFile
  }

  private fun getFile(e: AnActionEvent) = e.getData(CommonDataKeys.VIRTUAL_FILE)
}