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

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.ExecutorType
import com.google.idea.blaze.base.run.SourceToTargetFinder
import com.google.idea.blaze.base.run.TestTargetHeuristic
import com.google.idea.blaze.base.run.producers.RunConfigurationContext
import com.google.idea.blaze.base.run.producers.TestContext
import com.google.idea.blaze.base.run.producers.TestContextProvider
import com.google.idea.blaze.base.util.pluginProjectScope
import com.google.idea.blaze.cpp.CppBlazeRules.RuleTypes
import com.google.idea.sdkcompat.radler.RadTestPsiElement
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.util.asSafely
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import java.util.*

abstract class RadTestContextProvider : TestContextProvider {

  override fun getTestContext(context: ConfigurationContext): RunConfigurationContext? {
    val psiElement = context.psiLocation.asSafely<RadTestPsiElement>() ?: return null

    if (psiElement.test.framework != testFramework) {
      return null
    }

    val target = pluginProjectScope(context.project).future {
      val targets = findTargets(context).await()

      // orEmpty because of [FuturesUtil.getFirstFutureSatisfyingPredicate]
      chooseTargetForFile(context, targets.orEmpty())
    }

    return TestContext.builder(psiElement, ExecutorType.DEBUG_SUPPORTED_TYPES)
      .setTarget(target)
      .setTestFilter(createTestFilter(psiElement.test))
      .build()
  }

  protected abstract val testFramework: RadTestFramework

  protected abstract fun createTestFilter(test: RadTestElementModel): String?
}

private fun findTargets(context: ConfigurationContext): ListenableFuture<Collection<TargetInfo>?> {
  val virtualFile = context.location?.virtualFile ?: return Futures.immediateFuture(emptyList())

  return SourceToTargetFinder.findTargetInfoFuture(
    context.project,
    virtualFile.toNioPath().toFile(),
    Optional.of(RuleType.TEST)
  )
}

private fun chooseTargetForFile(context: ConfigurationContext, targets: Collection<TargetInfo>): TargetInfo? {
  val psiFile = context.psiLocation?.containingFile ?: return null
  val virtualFile = psiFile.virtualFile ?: return null

  val ccTargets = targets.filter { it -> it.kind == RuleTypes.CC_TEST.kind }

  return TestTargetHeuristic.chooseTestTargetForSourceFile(
    context.project,
    psiFile,
    virtualFile.toNioPath().toFile(),
    ccTargets,
    null,
  )
}
