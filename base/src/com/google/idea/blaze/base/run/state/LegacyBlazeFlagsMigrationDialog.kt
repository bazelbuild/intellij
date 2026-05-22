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
package com.google.idea.blaze.base.run.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Font
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val DIALOG_TITLE = "Bazel run-config flags removed"
private const val OK_BUTTON_TEXT = "Move to .bazelproject"
private const val CANCEL_BUTTON_TEXT = "Dismiss"

private const val OUTPUT_ROWS = 12
private const val OUTPUT_COLUMNS = 60

/** Shows the migration dialog on the EDT and returns the chosen exit code. */
internal fun showLegacyBlazeFlagsMigrationDialog(
  project: Project,
  affected: Map<String, List<String>>,
): Int {
  var exitCode = DialogWrapper.CANCEL_EXIT_CODE
  ApplicationManager.getApplication().invokeAndWait {
    val dialog = LegacyBlazeFlagsMigrationDialog(project, affected)
    dialog.show()
    exitCode = dialog.exitCode
  }

  return exitCode
}

/** Pure rendering of the affected configurations into the terminal-style block shown to the user. */
private fun renderAffected(affected: Map<String, List<String>>): String =
  affected.entries.joinToString("\n\n") { (name, flags) ->
    val body = flags.joinToString("\n") { "  -> $it" }
    "$name:\n$body"
  }

private class LegacyBlazeFlagsMigrationDialog(
  project: Project,
  private val affected: Map<String, List<String>>,
) : DialogWrapper(project) {

  init {
    title = DIALOG_TITLE
    setOKButtonText(OK_BUTTON_TEXT)
    setCancelButtonText(CANCEL_BUTTON_TEXT)
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row {
      text(
        "The user-editable \"Bazel flags\" field on run configurations was removed. " +
            "Each affected configuration's remaining flags are listed below:"
      )
    }
    row {
      val textArea = JBTextArea(renderAffected(affected), OUTPUT_ROWS, OUTPUT_COLUMNS).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
      }
      cell(JBScrollPane(textArea)).align(Align.FILL)
    }.resizableRow()
  }
}
