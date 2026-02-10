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
package com.google.idea.blaze.skylark.repl

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.Project
import icons.BlazeIcons
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.KeyStroke

private const val CONSOLE_TITLE = "Starlark REPL"
private const val FILE_EXTENSION = ".bzl"

class SkylarkReplConsole(
  project: Project,
  val handler: OSProcessHandler,
) : AbstractConsoleRunnerWithHistory<LanguageConsoleView>(project, CONSOLE_TITLE, null) {

  private lateinit var historyController: SkylarkConsoleHistoryController

  override fun createProcess() = handler.process

  override fun createProcessHandler(process: Process) = handler

  override fun getConsoleIcon(): Icon = BlazeIcons.BuildFile

  override fun createConsoleView(): LanguageConsoleView {
    @Suppress("UnstableApiUsage")
    val consoleView = LanguageConsoleBuilder().build(project, BuildFileLanguage.INSTANCE)
    consoleView.virtualFile.rename(this, consoleView.virtualFile.name + FILE_EXTENSION)
    consoleView.prompt = ">>"

    historyController = SkylarkConsoleHistoryController(consoleView)
    historyController.isMultiline = true
    historyController.install()

    HistoryUpAction(consoleView).registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)),
      consoleView.consoleEditor.component
    )

    HistoryDownAction(consoleView).registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)),
      consoleView.consoleEditor.component
    )

    return consoleView
  }

  override fun createExecuteActionHandler() = ProcessBackedConsoleExecuteActionHandler(handler, false)
}

// UP arrow navigates to previous history when caret is on first line
private class HistoryUpAction(private val consoleView: LanguageConsoleView) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ConsoleHistoryController.getController(consoleView)?.historyNext?.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    val editor = consoleView.currentEditor
    val onFirstLine = editor.document.getLineNumber(editor.caretModel.offset) == 0
    val noAutocomplete = LookupManager.getActiveLookup(editor) == null

    e.presentation.isEnabled = onFirstLine && noAutocomplete
    e.presentation.isVisible = false
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

// DOWN arrow navigates to next history when caret is on last line
class HistoryDownAction(private val consoleView: LanguageConsoleView) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ConsoleHistoryController.getController(consoleView)?.historyPrev?.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    val editor = consoleView.currentEditor
    val lineCount = editor.document.lineCount
    val onLastLine = lineCount == 0 || editor.document.getLineNumber(editor.caretModel.offset) == lineCount - 1
    val noAutocomplete = LookupManager.getActiveLookup(editor) == null

    e.presentation.isEnabled = onLastLine && noAutocomplete
    e.presentation.isVisible = false
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
