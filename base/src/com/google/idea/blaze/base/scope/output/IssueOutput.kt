/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.base.scope.output

import com.google.idea.blaze.base.io.VfsUtils
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Output
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.io.File

/** An issue in a blaze operation.  */
class IssueOutput (
  issue: BuildIssue,
  val kind: MessageEvent.Kind,
) : Output, BuildIssue by issue {

  companion object {
    @JvmStatic
    fun issue(kind: MessageEvent.Kind, title: String): Builder {
      return Builder(kind, title)
    }

    @JvmStatic
    fun error(title: String): Builder {
      return Builder(MessageEvent.Kind.ERROR, title)
    }

    @JvmStatic
    fun warn(title: String): Builder {
      return Builder(MessageEvent.Kind.WARNING, title)
    }
  }

  @Deprecated("used only for backwards compatibility")
  fun getMessage(): String = "$title\n$description"

  class Builder(private val kind: MessageEvent.Kind, private val title: String) {
    private var description: String = ""
    private var navigatable: (Project) -> Navigatable? = { null }
    private val fixes: MutableList<Pair<String, BuildIssueQuickFix>> = mutableListOf()

    fun withFile(file: File): Builder = withFile(file, line = 0, column = 0)

    fun withFile(file: File, line: Int = 0, column: Int = 0): Builder {
      val virtualFile = VfsUtils.resolveVirtualFile(file, false)

      if (virtualFile != null) {
        navigatable = { OpenFileDescriptor(it, virtualFile, line, column) }
      }

      return this
    }

    fun withNavigatable(navigatable: Navigatable): Builder {
      this.navigatable = { navigatable }
      return this
    }

    fun withDescription(description: String): Builder {
      this.description = description
      return this
    }

    fun withFix(description: String, fix: BuildIssueQuickFix): Builder {
      fixes.add(description to fix)
      return this
    }

    fun build(): IssueOutput {
      val builder = StringBuilder(description)

      if (fixes.isNotEmpty()) {
        builder.append("\n\nPossible fixes:\n")
      }

      for ((description, fix) in fixes) {
        builder.append("$description (<a href=\"${fix.id}\">click here</a>)")
      }

      val issue = object : BuildIssue {
        override val description: String
          get() = builder.toString()

        override val quickFixes: List<BuildIssueQuickFix>
          get() = fixes.map { it.second }

        override val title: String
          get() = this@Builder.title

        override fun getNavigatable(project: Project): Navigatable? = navigatable(project)
      }

      return IssueOutput(issue, kind)
    }

    fun submit(context: Context<*>) {
      context.output(build())
      if (kind == MessageEvent.Kind.ERROR) {
        context.setHasError()
      }
      if (kind == MessageEvent.Kind.WARNING) {
        context.setHasWarnings()
      }
    }
  }
}
