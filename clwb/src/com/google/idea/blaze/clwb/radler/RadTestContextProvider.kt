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

import com.google.common.util.concurrent.JdkFutureAdapters
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
import com.intellij.clion.radler.testing.RadTestPsiElement
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.application.readAction
import com.intellij.util.asSafely
import com.intellij.util.io.await
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.guava.asListenableFuture
import org.jetbrains.ide.PooledThreadExecutor
import java.util.*

class RadTestContextProvider : TestContextProvider {

  override fun getTestContext(context: ConfigurationContext): RunConfigurationContext? {
    val psiElement = context.psiLocation.asSafely<RadTestPsiElement>() ?: return null

    val support = RadTestFrameworkSupport.forFramework(psiElement.test.framework) ?: return null

    val target = pluginProjectScope(context.project).async {
      chooseTargetForFile(context, findTargets(context))
    }.asListenableFuture()

    return TestContext.builder(psiElement, ExecutorType.DEBUG_SUPPORTED_TYPES)
      .setTarget(target)
      .setTestFilter(support.createTestFilter(listOf(psiElement.test)))
      .build()
  }
}

private suspend fun findTargets(context: ConfigurationContext): Collection<TargetInfo> {
  val virtualFile = readAction{ context.location?.virtualFile } ?: return emptyList()

  return SourceToTargetFinder.findTargetInfoFuture(
    context.project,
    virtualFile.toNioPath().toFile(),
    Optional.of(RuleType.TEST),
  ).await()
}

private suspend fun chooseTargetForFile(context: ConfigurationContext, targets: Collection<TargetInfo>): TargetInfo? {
  val psiFile = readAction { context.psiLocation?.containingFile } ?: return null
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
