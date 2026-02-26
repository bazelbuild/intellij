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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExeclogParseAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle("Select Execution Log")
      .withDescription("Select a Bazel execution log file to parse")

    val selected = FileChooser.chooseFile(descriptor, project, null) ?: return

    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val text = ExeclogParseService.run(project, selected.path)

      withContext(Dispatchers.EDT) {
        val file = LightVirtualFile(selected.name, ExeclogFileType, text)
        file.isWritable = false
        SingleRootFileViewProvider.doNotCheckFileSizeLimit(file)

        FileEditorManager.getInstance(project).openFile(file, true)
      }
    }
  }
}
