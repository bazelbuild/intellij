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
package com.google.idea.blaze.base.sync.aspects.impl.legacy

import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.sync.aspects.storage.AspectStorageService
import com.google.idea.blaze.base.sync.aspects.storage.AspectWriter
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path
import java.util.*

/**
 * The legacy aspect strategy: the bundled aspect is materialized (templated) into the workspace and
 * referenced by a single `--aspects` flag.
 */
class LegacyAspectStrategy : AspectStrategy() {

  class Provider : AspectStrategyProvider {
    override fun getStrategy(): AspectStrategy? {
      if (Registry.`is`("bazel.sync.use.intellij.aspect")) return null
      return LegacyAspectStrategy()
    }
  }

  override fun getName(): String = "AspectStrategySkylarkBazel"

  override fun prefix(): Path = Path.of("legacy")

  override fun writers(): List<AspectWriter> = listOf(
    AspectWriterImpl(),
    AspectTemplateWriter(),
    CcAspectTemplateWriter(),
  )

  override fun resolve(project: Project, relativePath: String): Optional<Label> {
    return AspectStorageService.of(project).resolve(relativePath, prefix())
  }

  override fun getAspectFlag(project: Project, activeLanguages: Set<LanguageClass>): Optional<String> {
    return resolve(project, "intellij_info_bundled.bzl").map { "--aspects=$it%intellij_info_aspect" }
  }

  override fun genericOutputGroup(outputGroup: OutputGroup): Optional<String> {
    return if (outputGroup == OutputGroup.INFO) Optional.of("${outputGroup.prefix}-generic") else Optional.empty()
  }
}
