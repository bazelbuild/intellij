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
package com.google.idea.blaze.base.execlog

import com.intellij.largeFilesEditor.editor.LargeFileEditor
import com.intellij.largeFilesEditor.editor.LargeFileEditorProvider
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private const val PROVIDER_ID = "BlazeExeclogEditorProvider"

class ExeclogEditorProvider : FileEditorProvider, DumbAware {

  private val largeFileEditorProvider = LargeFileEditorProvider()

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.extension == ExeclogBundleProvider.EXECLOG_FILE_EXTENSION
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val editor = largeFileEditorProvider.createEditor(project, file) as LargeFileEditor

    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
    editor.trySetHighlighter(highlighter)

    editor.editor.settings.isLineNumbersShown = true
    editor.editor.settings.isUseSoftWraps = true

    return editor
  }

  override fun getEditorTypeId(): String = PROVIDER_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
