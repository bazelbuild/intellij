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

    return formatConfiguration(config)
  }

  override fun shouldShowOutputInEditor(): Boolean = true

  private fun formatConfiguration(config: BlazeResolveConfiguration): String {
    val builder = StringBuilder()
    builder.appendLine("display name: ${config.displayName}")

    val configData = config.configurationData
    builder.appendLine("configuration ID: ${configData.configurationId()}")

    val compiler = configData.compilerSettings()
    builder.appendLine("compiler: ${compiler.name()}")
    builder.appendLine("  -> C compiler: ${compiler.cCompiler()}")
    builder.appendLine("  -> C++ compiler: ${compiler.cppCompiler()}")
    builder.appendLine("  -> sysroot: ${compiler.sysroot()}")

    printStringList(builder, "targets", config.targets.map { it.label().toString() })
    printStringList(builder, "sources", config.sources.values.flatten().map { it.path })
    printStringList(builder, "local copts", configData.localCopts())
    printStringList(builder, "local conlyopts", configData.localConlyopts())
    printStringList(builder, "local cxxopts", configData.localCxxopts())
    printStringList(builder, "defines", configData.transitiveDefines())
    printStringList(builder, "includes", configData.transitiveIncludeDirectories().map { it.path().toString() })
    printStringList(builder, "quote includes", configData.transitiveQuoteIncludeDirectories().map { it.path().toString() })
    printStringList(builder, "system includes", configData.transitiveSystemIncludeDirectories().map { it.path().toString() })

    return builder.toString()
  }
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
