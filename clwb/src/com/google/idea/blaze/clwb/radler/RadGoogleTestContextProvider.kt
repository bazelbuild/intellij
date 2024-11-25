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
import com.google.idea.blaze.cpp.CppBlazeRules.RuleTypes
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.util.asSafely
import com.jetbrains.cidr.radler.testing.RadTestPsiElement
import com.jetbrains.rider.model.RadTestFramework
import org.jetbrains.ide.PooledThreadExecutor
import java.io.File
import java.util.*

class RadGoogleTestContextProvider : TestContextProvider {

  override fun getTestContext(context: ConfigurationContext): RunConfigurationContext? {
    val psiElement = context.psiLocation.asSafely<RadTestPsiElement>() ?: return null

    if (psiElement.test.framework != RadTestFramework.GTest) {
      return null
    }

    val targets = findTargets(context)
    val target = Futures.transform(targets, { chooseTargetForFile(context, it) }, PooledThreadExecutor.INSTANCE)

    return TestContext.builder(psiElement, ExecutorType.DEBUG_SUPPORTED_TYPES)
      .setTarget(target)
      .build()
  }
}

private fun findTargets(context: ConfigurationContext): ListenableFuture<Collection<TargetInfo>> {
  val virtualFile = context.location?.virtualFile ?: return Futures.immediateFuture(emptyList())

  return SourceToTargetFinder.findTargetInfoFuture(
    context.project,
    File(virtualFile.path),
    Optional.of(RuleType.TEST),
  ) ?: Futures.immediateFuture(emptyList())
}

private fun chooseTargetForFile(context: ConfigurationContext, targets: Collection<TargetInfo>): TargetInfo? {
  val psiFile = context.psiLocation?.containingFile ?: return null
  val virtualFile = psiFile.virtualFile ?: return null

  val ccTargets = targets.filter { it -> it.kind == RuleTypes.CC_TEST.kind }

  return TestTargetHeuristic.chooseTestTargetForSourceFile(
    context.project,
    psiFile,
    File(virtualFile.path),
    ccTargets,
    null,
  )
}