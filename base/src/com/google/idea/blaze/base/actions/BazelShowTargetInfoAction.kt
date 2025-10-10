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
package com.google.idea.blaze.base.actions

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.protobuf.TextFormat
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.testFramework.LightVirtualFile

private val LOG = Logger.getInstance(BazelShowTargetInfoAction::class.java)

class BazelShowTargetInfoAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      LOG.warn("no open project found")
      return
    }

    val projectData = BlazeProjectDataManager.getInstance(project).blazeProjectData
    if (projectData == null) {
      LOG.warn("no project data found")
      return
    }

    projectData.targetMap.targets()

    JBPopupFactory.getInstance()
      .createListPopup(TargetPopupStep(project, projectData.targetMap.targets().toList()))
      .showCenteredInCurrentWindow(project)
  }
}

private class TargetPopupStep(val project: Project, candidates: List<TargetIdeInfo>) :
  BaseListPopupStep<TargetIdeInfo>("Targets", candidates) {

  override fun getTextFor(value: TargetIdeInfo): String {
    return String.format("%s (%s)", value.key.label.toString(), value.kind)
  }

  override fun onChosen(selectedValue: TargetIdeInfo, finalChoice: Boolean): PopupStep<*>? {
    val proto = TextFormat.printer().printToString(selectedValue.toProto())
    val fileName = String.format("Target Info for %s (%s)", selectedValue.key.label, selectedValue.kind)
    val file = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, proto)
    FileEditorManager.getInstance(project).openFile(file, false)

    return super.onChosen(selectedValue, true)
  }

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }
}