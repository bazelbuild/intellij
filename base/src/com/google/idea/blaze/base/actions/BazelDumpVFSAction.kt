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

import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.util.VfsUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFileManager

private val LOG = Logger.getInstance(BazelDumpVFSAction::class.java)

class BazelDumpVFSAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      LOG.warn("no open project found")
      return
    }

    val data = BlazeProjectDataManager.getInstance(project).blazeProjectData
    if (data == null) {
      LOG.warn("no project data found")
      return
    }

    val root = data.blazeInfo.executionRoot
    if (root == null) {
      LOG.warn("no execution root found")
      return
    }

    val virtualRoot = VirtualFileManager.getInstance().findFileByNioPath(root.toPath())
    if (virtualRoot == null) {
      LOG.warn("no virtual file found for workspace root")
      return
    }

    LOG.info("################################## EXECROOT VFS ################################")
    VfsUtil.getVfsChildrenAsSequence(virtualRoot).forEach { LOG.info(it.toString()) }
    LOG.info("################################################################################")
  }
}
