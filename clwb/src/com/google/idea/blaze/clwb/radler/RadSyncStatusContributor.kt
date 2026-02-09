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