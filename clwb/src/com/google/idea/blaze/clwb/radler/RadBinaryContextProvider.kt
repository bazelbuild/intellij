/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.SourceToTargetFinder
import com.google.idea.blaze.base.run.producers.BinaryContextProvider
import com.google.idea.blaze.base.run.producers.BinaryContextProvider.BinaryRunContext
import com.google.idea.blaze.cpp.CppBlazeRules.RuleTypes
import com.google.idea.sdkcompat.radler.RadSymbolHost
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import java.util.*

/**
 * This run configuration provider creates configurations for the gutter icon created by the
 * [com.jetbrains.cidr.cpp.runFile.nova.CppFileNovaRunLineMarkerProvider], since the actual gutter icon provider for
 * radler requires a [com.jetbrains.cidr.execution.CidrTargetRunConfigurationProducer] and it would not be feasible to
 * implement one here.
 */
class RadBinaryContextProvider : BinaryContextProvider {

  override fun getRunContext(context: ConfigurationContext): BinaryRunContext? {
    if (!isMain(context.psiLocation)) return null

    val target = findTargets(context).firstOrNull() ?: return null

    return BinaryRunContext.create(context.psiLocation, target)
  }
}

private fun isMain(element: PsiElement?): Boolean {
  if (element !is LeafElement) return false

  val symbolsHost = RadSymbolHost.getInstance(element.project)
  return symbolsHost.isEntryPointOffset(element.containingFile.viewProvider.virtualFile, element.startOffset)
}

private fun findTargets(context: ConfigurationContext): Collection<TargetInfo> {
  val virtualFile = context.location?.virtualFile ?: return emptyList()

  val targets = SourceToTargetFinder.findTargetsForSourceFile(
    context.project,
    virtualFile.toNioPath().toFile(),
    Optional.of(RuleType.BINARY),
  ) ?: return emptyList()

  return targets.filter { it -> it.kind == RuleTypes.CC_BINARY.kind }
}
