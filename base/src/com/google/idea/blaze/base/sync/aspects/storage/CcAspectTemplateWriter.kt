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

import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider.ASPECT_TEMPLATE_DIRECTORY
import com.google.idea.blaze.base.util.TemplateWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path

private const val TEMPLATE_CC_INFO = "cc_info.template.bzl"
private const val REALIZED_CC_INFO = "cc_info.bzl"

class CcAspectTemplateWriter : AspectWriter {

  override fun name(): String = "CC Aspect Templates"

  override fun writeDumb(dst: Path, project: Project) {
    val options = mapOf(
      getRegistryOption("bazel.cc.aspect.use_get_tool_for_action", default = true),
      getRegistryOption("bazel.cc.virtual.includes.cache.enabled", default = false, aka = "collect_dependencies"),
    )

    TemplateWriter.evaluate(
      dst,
      REALIZED_CC_INFO,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_CC_INFO,
      options,
    )
  }

  private fun getRegistryOption(key: String, default: Boolean, aka: String? = null): Pair<String, String> {
    val value = Registry.`is`(key, default)
    val name = aka ?: key.split('.').last()
    return Pair(name, if (value) "True" else "False")
  }
}
