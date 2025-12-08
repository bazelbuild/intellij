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
package com.google.idea.blaze.base.actions.debug

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.protobuf.TextFormat
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class BazelShowTargetInfo : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val info = withContext(Dispatchers.EDT) {
      suspendCancellableCoroutine { cont ->
        JBPopupFactory.getInstance()
          .createListPopup(TargetPopupStep(cont, data.targetMap.targets().toList()))
          .showCenteredInCurrentWindow(project)
      }
    }

    return TextFormat.printer().printToString(info.toProto())
  }

  override fun shouldShowOutputInEditor(): Boolean = true
}

private class TargetPopupStep(
  private val cont: CancellableContinuation<TargetIdeInfo>,
  candidates: List<TargetIdeInfo>,
) : BaseListPopupStep<TargetIdeInfo>("Targets", candidates) {

  override fun getTextFor(value: TargetIdeInfo): String {
    return String.format("%s (%s)", value.key.label.toString(), value.kind)
  }

  override fun onChosen(selectedValue: TargetIdeInfo, finalChoice: Boolean): PopupStep<*>? {
    cont.resumeWith(Result.success(selectedValue))
    return super.onChosen(selectedValue, true)
  }

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }
}