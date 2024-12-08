package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.SourceToTargetFinder
import com.google.idea.blaze.base.run.producers.BinaryContextProvider
import com.google.idea.blaze.base.run.producers.BinaryContextProvider.BinaryRunContext
import com.google.idea.blaze.cpp.CppBlazeRules.RuleTypes
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.jetbrains.cidr.radler.protocol.RadSymbolsHost
import java.io.File
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

  val symbolsHost = RadSymbolsHost.getInstance(element.project)
  return symbolsHost.isEntryPointOffset(element.containingFile.viewProvider.virtualFile, element.startOffset)
}

private fun findTargets(context: ConfigurationContext): Collection<TargetInfo> {
  val virtualFile = context.location?.virtualFile ?: return emptyList()

  val targets = SourceToTargetFinder.findTargetsForSourceFile(
    context.project,
    File(virtualFile.path),
    Optional.of(RuleType.BINARY),
  ) ?: return emptyList()

  return targets.filter { it -> it.kind == RuleTypes.CC_BINARY.kind }
}
