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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.sync.SyncProjectState
import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider.ASPECT_TEMPLATE_DIRECTORY
import com.google.idea.blaze.base.util.TemplateWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path

private const val TEMPLATE_CC_INFO = "cc_info.template.bzl"
private const val REALIZED_CC_INFO = "cc_info.bzl"

class CcAspectTemplateWriter : AspectWriter {

  override fun name(): String = "CC Aspect Templates"

  override fun write(dst: Path, project: Project, state: SyncProjectState) {
    val options = mapOf(
      getRegistryOption("bazel.cc.aspect.use_get_tool_for_action", true),
      getCcEnabledOption(state),
      getAtLeastBazel9Option(state),
    )

    TemplateWriter.evaluate(
      dst,
      REALIZED_CC_INFO,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_CC_INFO,
      options,
    )
  }

  private fun getCcEnabledOption(state: SyncProjectState): Pair<String, String> {
    val hasRulesCc = state.languageSettings.activeLanguages.contains(LanguageClass.C)
      && state.externalWorkspaceData?.getByRepoName("rules_cc") != null
    return Pair("isCcEnabled", if (hasRulesCc) "true" else "false")
  }

  private fun getAtLeastBazel9Option(state: SyncProjectState): Pair<String, String> {
    val isAtLeastBazel9 = state.blazeVersionData.bazelIsAtLeastVersion(9, 0, 0)
    return Pair("bazel9OrAbove", if (isAtLeastBazel9) "true" else "false")
  }

  private fun getRegistryOption(key: String, default: Boolean): Pair<String, String> {
    val value = Registry.`is`(key, default)
    val name = key.split('.').last()
    return Pair(name, if (value) "True" else "False")
  }
}
