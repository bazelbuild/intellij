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
package com.google.idea.balze.skylark.repl

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import icons.BlazeIcons
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon

private const val CONSOLE_TITLE = "Starlark REPL"
private const val FILE_EXTENSION = ".bzl"

class SkylarkReplConsole(
  project: Project,
  val handler: OSProcessHandler,
) : AbstractConsoleRunnerWithHistory<LanguageConsoleView>(project, CONSOLE_TITLE, null) {

  // WHY DO I HAVE TO DO THIS MANUALLY IF I EXTEND ABSTRACTCONSOLERUNNERWITHHISTORY???
  private lateinit var manager: HistoryManger

  override fun createProcess() = handler.process

  override fun createProcessHandler(process: Process) = handler

  override fun getConsoleIcon(): Icon = BlazeIcons.BuildFile

  override fun createConsoleView(): LanguageConsoleView {
    @Suppress("UnstableApiUsage")
    val consoleView = LanguageConsoleBuilder().build(project, BuildFileLanguage.INSTANCE)
    consoleView.virtualFile.rename(this, consoleView.virtualFile.name + FILE_EXTENSION)
    consoleView.prompt = ">>"

    manager = HistoryManger(consoleView.consoleEditor)
    consoleView.consoleEditor.contentComponent.addKeyListener(manager)
    consoleView.consoleEditor.isOneLineMode = true

    return consoleView
  }

  override fun createExecuteActionHandler() = object : ProcessBackedConsoleExecuteActionHandler(handler, false) {
    override fun sendText(text: String?) {
      if (!text.isNullOrBlank()) manager.add(text)
      super.sendText(text)
    }
  }
}

private class HistoryManger(private val editor: EditorEx) : KeyAdapter() {

  private val history = mutableListOf<String>()
  private var historyIndex = 0

  fun add(entry: String) {
    history.add(entry.removeSuffix("\n"))
    historyIndex = history.size - 1
  }

  override fun keyReleased(event: KeyEvent) {
    val entry = getNextEntry(event) ?: return

    WriteAction.run<Throwable> {
      editor.document.setText(entry)
      editor.caretModel.moveToOffset(entry.length)
    }
  }

  private fun getNextEntry(event: KeyEvent): String? {
    if (history.isEmpty()) return null

    if (event.keyCode == KeyEvent.VK_UP && editor.document.text != history[historyIndex]) {
      return history[historyIndex]
    }
    if (event.keyCode == KeyEvent.VK_UP && historyIndex > 0) {
      return history[--historyIndex]
    }
    if (event.keyCode == KeyEvent.VK_DOWN && historyIndex < (history.size - 1)) {
      return history[++historyIndex]
    }

    return null
  }
}