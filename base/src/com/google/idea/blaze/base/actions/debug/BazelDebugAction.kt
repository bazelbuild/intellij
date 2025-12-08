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

import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.launch

private val LOG = Logger.getInstance(BazelDebugAction::class.java)

abstract class BazelDebugAction : DumbAwareAction() {

  final override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Blaze.isBlazeProject(e.project)
  }

  final override fun actionPerformed(e: AnActionEvent) {
    currentThreadCoroutineScope().launch { entryPoint(e) }
  }

  private suspend fun entryPoint(e: AnActionEvent) {
    val builder = StringBuilder()

    builder.appendLine("################################################################################")
    builder.appendLine(String.format("# %-76s #", javaClass.simpleName))
    builder.appendLine("################################################################################")

    try {
      val project = e.project
        ?: fail("no open project found")

      val data = BlazeProjectDataManager.getInstance(project).blazeProjectData
        ?: fail("no project data found")

      builder.append(exec(project, data))
    } catch (ex: DebugActionFailed) {
      builder.appendLine("ERROR: ${ex.message}")
    }

    builder.appendLine("################################################################################")

    val result = builder.toString()
    for (line in result.lines()) {
      LOG.info(line)
    }
    if (shouldShowOutputInEditor()) {
      showOutputInEditor(e, result)
    }
  }

  private fun showOutputInEditor(e: AnActionEvent, text: String) {
    val project = e.project ?: return

    val file = LightVirtualFile("${javaClass.simpleName}.txt", PlainTextFileType.INSTANCE, text)
    FileEditorManager.getInstance(project).openFile(file, false)
  }

  protected open fun shouldShowOutputInEditor(): Boolean = ApplicationManager.getApplication().isInternal

  @Throws(DebugActionFailed::class)
  protected fun fail(reason: String): Nothing = throw DebugActionFailed(reason)

  @Throws(DebugActionFailed::class)
  protected abstract suspend fun exec(project: Project, data: BlazeProjectData): String
}

private class DebugActionFailed(reason: String) : Exception(reason)