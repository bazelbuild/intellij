@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.base.buildview.events

import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils
import com.google.idea.blaze.base.util.pluginProjectScope
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.pom.NavigatableAdapter
import kotlinx.coroutines.launch

data class BazelBuildIssue(
  override val title: String,
  override val description: String,
  override val quickFixes: List<BuildIssueQuickFix> = emptyList(),
  val label: String? = null,
) : BuildIssue {
  override fun getNavigatable(project: Project): Navigatable? {
    if (label == null) return null
    return LabelNavigatable(project, label)
  }
}

private class LabelNavigatable(private val project: Project, private val label: String) : NavigatableAdapter() {
  override fun navigate(requestFocus: Boolean) {
    pluginProjectScope(project).launch {
      val descriptor = readAction { findFileAsync() }
      if (descriptor != null && descriptor.canNavigate()) {
        invokeLater {
          descriptor.navigate(requestFocus)
        }
      }
    }
  }

  private fun findFileAsync(): OpenFileDescriptor? {
    // TODO: better use `bazel query "label" --output=location` - would be way more accurate ??

    val label = LabelUtils.createLabelFromString(null, label) ?: return null
    val element = BuildReferenceManager.getInstance(project).resolveLabel(label) ?: return null

    val file = element.containingFile.virtualFile ?: return null
    return OpenFileDescriptor(project, file, element.textRange.startOffset)
  }
}