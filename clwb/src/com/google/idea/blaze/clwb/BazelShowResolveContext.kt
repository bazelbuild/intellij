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
package com.google.idea.blaze.clwb

import com.google.idea.blaze.base.actions.debug.BazelDebugAction
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.cpp.BlazeResolveConfiguration
import com.google.idea.blaze.cpp.BlazeCWorkspace
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class BazelShowResolveContext : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val config = withContext(Dispatchers.EDT) {
      suspendCancellableCoroutine { cont ->
        JBPopupFactory.getInstance()
          .createListPopup(ResolveConfigPopupStep(cont, BlazeCWorkspace.getInstance(project).resolveConfigurations))
          .showCenteredInCurrentWindow(project)
      }
    }

    return config.configurationData.toPrettyString() + "\n"
  }

  override fun shouldShowOutputInEditor(): Boolean = true
}

private fun printStringList(builder: StringBuilder, name: String, values: List<String>) {
  if (values.isEmpty()) {
    builder.appendLine("$name (0): empty")
    return
  }

  builder.appendLine("$name (${values.size}):")
  for (value in values) {
    builder.appendLine("  -> $value")
  }
}

private class ResolveConfigPopupStep(
  private val cont: CancellableContinuation<BlazeResolveConfiguration>,
  candidates: List<BlazeResolveConfiguration>,
) : BaseListPopupStep<BlazeResolveConfiguration>("Resolve Configurations", candidates) {

  override fun getTextFor(value: BlazeResolveConfiguration): String {
    return value.displayName
  }

  override fun onChosen(selectedValue: BlazeResolveConfiguration, finalChoice: Boolean): PopupStep<*>? {
    cont.resumeWith(Result.success(selectedValue))
    return super.onChosen(selectedValue, true)
  }

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }

  override fun canceled() {
    cont.cancel()
    super.canceled()
  }
}
