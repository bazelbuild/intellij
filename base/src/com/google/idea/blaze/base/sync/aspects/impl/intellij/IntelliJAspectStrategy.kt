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
package com.google.idea.blaze.base.sync.aspects.impl.intellij

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.sync.aspects.storage.AspectStorageService
import com.google.idea.blaze.base.sync.aspects.storage.AspectWriter
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyProvider
import com.intellij.aspect.lib.Aspects
import com.intellij.aspect.lib.Rules
import com.intellij.aspect.lib.OutputGroups
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * The IntelliJ split aspect strategy: the `intellij_aspect_sdk` archive is materialized by
 * [IntelliJAspectWriter] into the `intellij` prefix, and the `--aspects` flag is built by resolving
 * each aspect entry against that same deployed location, so the flag and the files always agree.
 */
class IntelliJAspectStrategy : AspectStrategy() {

  class Provider : AspectStrategyProvider {
    override fun getStrategy(): AspectStrategy? {
      if (!Registry.`is`("bazel.sync.use.intellij.aspect")) return null
      return IntelliJAspectStrategy()
    }
  }

  override fun getName(): String = "IntelliJStrategy"

  override fun prefix(): Path = Path.of("intellij")

  override fun writers(): List<AspectWriter> = listOf(IntelliJAspectWriter())

  override fun resolve(project: Project, relativePath: String): Optional<Label> {
    return AspectStorageService.of(project).resolve(relativePath, prefix())
  }

  override fun getAspectFlag(project: Project, activeLanguages: Set<LanguageClass>): Optional<String> {
    val aspects = Aspects.forRules(toAspectRules(activeLanguages)).mapNotNull { aspect ->
      resolve(project, "${aspect.pkg}/${aspect.file}").map { "$it%${aspect.aspect}" }.getOrNull()
    }

    if (aspects.isEmpty()) return Optional.empty()
    return Optional.of("--aspects=" + aspects.joinToString(","))
  }

  override fun genericOutputGroup(outputGroup: OutputGroup): ImmutableList<String> {
    // we do not support the light weight "SYNC" without the build output group yet
    return ImmutableList.of(OutputGroups.INFO.groupName, OutputGroups.SYNC.groupName, OutputGroups.BUILD.groupName)
  }
}

private fun toAspectRules(active: Set<LanguageClass>): Set<Rules> = buildSet {
  if (LanguageClass.C in active) add(Rules.CC)
  if (LanguageClass.PYTHON in active) add(Rules.PYTHON)
}
