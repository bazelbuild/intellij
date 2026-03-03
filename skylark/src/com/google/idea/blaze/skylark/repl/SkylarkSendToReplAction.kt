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
package com.google.idea.blaze.skylark.repl.send

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType
import com.google.idea.blaze.skylark.repl.REPL_JAR_PATH
import com.google.idea.blaze.skylark.repl.SkylarkReplActiveConsoleService
import com.google.idea.blaze.skylark.repl.SkylarkReplConsole
import com.google.idea.common.util.RunJarService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import icons.BlazeIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.Icon

abstract class SkylarkSendToReplAction : IntentionAction, Iconable {

  override fun getFamilyName(): @IntentionFamilyName String = "Send to Starlark REPL"

  override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return editor != null && file is BuildFile && file.blazeFileType == BlazeFileType.SkylarkExtension
  }

  override fun getIcon(flags: Int): Icon = BlazeIcons.BuildFile

  class Line : SkylarkSendToReplAction() {

    override fun getText(): @IntentionName String = "Send Line to Starlark REPL"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
      return super.isAvailable(project, editor, file) && !editor!!.selectionModel.hasSelection()
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      if (editor == null) return
      val lineNumber = editor.caretModel.logicalPosition.line

      val lineText = editor.document.getText(
        TextRange(
          editor.document.getLineStartOffset(lineNumber),
          editor.document.getLineEndOffset(lineNumber),
        )
      )

      sendToRepl(project, lineText)
    }
  }

  class Selection : SkylarkSendToReplAction() {

    override fun getText(): @IntentionName String = "Send Selection to Starlark REPL"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
      return super.isAvailable(project, editor, file) && editor!!.selectionModel.hasSelection()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      val text = editor?.selectionModel?.selectedText ?: return
      sendToRepl(project, text)
    }
  }
}

private fun sendToRepl(project: Project, text: String) {
  val console = SkylarkReplActiveConsoleService.getInstance(project).findRunning()
  if (console != null) {
    console.sendText(text)
    console.showConsole()
    return
  }

  currentThreadCoroutineScope().launch {
    val handler = RunJarService.run(REPL_JAR_PATH)

    withContext(Dispatchers.EDT) {
      val newConsole = SkylarkReplConsole(project, handler)
      newConsole.initAndRun()
      newConsole.sendText(text)
    }
  }
}

