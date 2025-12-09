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

import com.google.idea.common.util.RunJarService
import com.google.idea.common.util.Datafiles
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val REPL_JAR_PATH by Datafiles.resolveLazy("bazel/starlark_repl.jar")

class SkylarkReplAction() : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return

    currentThreadCoroutineScope().launch {
      val handler = RunJarService.run(REPL_JAR_PATH)

      withContext(Dispatchers.EDT) {
        SkylarkReplConsole(project, handler).initAndRun()
      }
    }
  }
}