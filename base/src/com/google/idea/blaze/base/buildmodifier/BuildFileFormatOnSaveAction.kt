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
package com.google.idea.blaze.base.buildmodifier

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs buildifier on BUILD/Starlark files when they are saved.
 *
 * Formatting is delegated to [BuildifierFormattingService] through the platform's formatting
 * pipeline, so this only has to decide *whether* to format.
 */
class BuildFileFormatOnSaveAction : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {

  override fun isEnabledForProject(project: Project): Boolean = BlazeUserSettings.getInstance().formatBuildFilesOnSave

  override val presentableName: String = "Running buildifier"

  override suspend fun updateDocument(project: Project, document: Document) {
    if (!BlazeUserSettings.getInstance().formatBuildFilesOnSave) return

    val buildFile = readAction {
      PsiDocumentManager.getInstance(project).getPsiFile(document)
        ?.takeIf { it is BuildFile && it.isValid && it.isWritable }
    } ?: return

    // buildifier has no range mode, so always format the entire file.
    val processor = ReformatCodeProcessor(project, arrayOf(buildFile), null, /* processChangedTextOnly= */ false)
    processor.setProcessAllFilesAsSingleUndoStep(false)

    withContext(Dispatchers.Default) {
      coroutineToIndicator { processor.processFilesUnderProgress(it) }
    }
  }
}
