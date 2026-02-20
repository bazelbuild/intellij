package com.google.idea.blaze.skylark.repl

import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.console.ConsoleRootType
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

/**
 * Custom history controller for Starlark REPL that properly handles multi-line history
 * navigation by replacing the entire console input area instead of just the current line.
 */
class SkylarkConsoleHistoryController(
  consoleView: LanguageConsoleView,
) : ConsoleHistoryController(SkylarkConsoleRootType, null, consoleView) {

  override fun insertTextMultiline(text: CharSequence, editor: Editor, document: Document): Int {
    // replace the entire document content with the history entry
    document.replaceString(0, document.textLength, text)

    // select the inserted text
    editor.selectionModel.setSelection(0, text.length)

    // return the start offset (0) for caret positioning
    return 0
  }
}

private object SkylarkConsoleRootType : ConsoleRootType("starlark-repl", "Starlark REPL") {

  override fun getDefaultFileExtension(): String = "bzl"
}
