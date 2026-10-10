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
package com.google.idea.blaze.base.sync.actions

import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Base class for sync actions.  */
abstract class BlazeProjectSyncAction : BlazeProjectAction(), DumbAware {
  protected abstract fun runSync(project: Project?, e: AnActionEvent?)

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
        runSync(project, e)
      }

      withContext(Dispatchers.EDT) {
        updateStatus(project, e)
      }
    }
  }

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    updateStatus(project, e)
  }

  companion object {
    private fun updateStatus(project: Project, e: AnActionEvent) {
      val presentation = e.presentation
      presentation.isEnabled = !BlazeSyncStatus.getInstance(project).syncInProgress()
    }
  }
}
