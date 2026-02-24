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
package com.google.idea.blaze.skylark.repl

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.LinkedList

@Service(Service.Level.PROJECT)
class SkylarkReplActiveConsoleService {

  companion object {
    fun getInstance(project: Project): SkylarkReplActiveConsoleService = project.service()
  }

  private val consoles = LinkedList<SkylarkReplConsole>()

  @Synchronized
  fun register(console: SkylarkReplConsole, view: LanguageConsoleView) {
    consoles.addFirst(console)

    view.consoleEditor.contentComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) = onConsoleFocused(console)
    })
  }

  @Synchronized
  private fun onConsoleFocused(console: SkylarkReplConsole) {
    consoles.remove(console)
    consoles.addFirst(console)
  }

  @Synchronized
  fun findRunning(): SkylarkReplConsole? {
    val iter = consoles.iterator()

    while (iter.hasNext()) {
      val console = iter.next()

      if (!console.handler.isProcessTerminated) {
        return console
      } else {
        iter.remove()
      }
    }

    return null
  }

}
