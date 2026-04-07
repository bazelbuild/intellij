/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.syncstatus.LegacySyncStatusContributor
import com.google.idea.blaze.base.syncstatus.LegacySyncStatusContributor.*
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.cpp.fileType.CppFileType
import com.jetbrains.rider.cpp.fileType.psi.CppFile

class RadSyncStatusContributor : LegacySyncStatusContributor {

  override fun toPsiFileAndName(
    projectData: BlazeProjectData,
    node: ProjectViewNode<*>,
  ): PsiFileAndName? {
    if (node !is PsiFileNode) return null

    val file = node.value
    if (file !is CppFile) return null

    return PsiFileAndName(file, file.getName())
  }

  override fun handlesFile(projectData: BlazeProjectData, file: VirtualFile): Boolean {
    if (!projectData.workspaceLanguageSettings().isLanguageActive(LanguageClass.C)) {
      return false;
    }

    val fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
    return fileType is CppFileType
  }
}