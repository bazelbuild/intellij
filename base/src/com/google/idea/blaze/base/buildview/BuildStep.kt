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
package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope
import com.google.idea.blaze.base.scope.scopes.TimingScope
import com.google.idea.blaze.base.util.pluginProjectScope
import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.Future

/**
 * Thrown by a [BuildStep] via [fail] to signal a user-facing failure with a pre-formatted message.
 *
 * [execute] / [tryExecute] catch this, submit an [IssueOutput] on the surrounding context, and
 * unwind via a fresh [CancellationException] so the failure is reported exactly once and the
 * enclosing [launchBuild] completes without a second user facing error.
 */
class BuildStepFailed(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {

  override val message: String get() = super.message!!
}

/** A unit of work that runs inside a [BlazeContext] as part of a [launchBuild]. */
interface BuildStep<T> {

  val title: String

  @Throws(BuildStepFailed::class)
  fun run(ctx: BlazeContext): T
}

/**
 * Runs the step on a child context. A [BuildStepFailed] (from [fail]) or any other unexpected
 * throwable is reported to [ctx] and then rethrown as a [CancellationException] so the enclosing
 * build coroutine unwinds without emitting a second error.
 */
fun <T> BuildStep<T>.execute(ctx: BlazeContext): T {
  return BlazeContext.create(ctx).use { childCtx ->
    childCtx.push(TimingScope(title, TimingScope.EventType.BlazeInvocation))
    childCtx.setPropagatesErrors(true)

    try {
      run(childCtx)
    } catch (e: CancellationException) {
      throw e
    } catch (e: BuildStepFailed) {
      IssueOutput.error("$title failed").withDescription(e.message).submit(ctx)
      throw CancellationException("$title failed")
    } catch (e: Throwable) {
      IssueOutput.error("$title failed").withThrowable(e).submit(ctx)
      throw CancellationException("$title failed")
    }
  }
}

/** Like [execute], but reports failures as warnings and returns `null` on failure. */
fun <T> BuildStep<T>.tryExecute(ctx: BlazeContext): T? {
  return BlazeContext.create(ctx).use { childCtx ->
    childCtx.push(TimingScope(title, TimingScope.EventType.BlazeInvocation))
    childCtx.setPropagatesErrors(false)

    try {
      run(childCtx)
    } catch (e: CancellationException) {
      throw e
    } catch (e: BuildStepFailed) {
      IssueOutput.warn("$title skipped").withDescription(e.message).submit(ctx)
      null
    } catch (e: Throwable) {
      IssueOutput.warn("$title skipped").withThrowable(e).submit(ctx)
      null
    }
  }
}

/** Aborts the step with a user-facing message. [execute] translates this into an [IssueOutput]. */
fun fail(message: String, cause: Throwable? = null): Nothing {
  throw BuildStepFailed(message, cause)
}

/** Emits a warning on [ctx], titled with the step's [BuildStep.title]. */
fun BuildStep<*>.warn(ctx: BlazeContext, message: String) {
  IssueOutput.warn(title).withDescription(message).submit(ctx)
}

/** The body of a [launchBuild] coroutine. A SAM interface for Java interop. */
fun interface BuildBody<T> {

  @Throws(ExecutionException::class)
  fun run(ctx: BlazeContext): T?
}

/**
 * Launches [body] on the plugin project scope with a root [BlazeContext] and a [BuildViewScope]
 * titled [title]. Returns a [Future] resolved with [body]'s result, or cancelled when a step
 * inside [body] calls [fail].
 */
@Throws(ExecutionException::class)
fun <T> launchBuild(project: Project, title: String, body: BuildBody<T>): Future<T> {
  return pluginProjectScope(project).async(Dispatchers.Default) {
    BlazeContext.create().use { ctx ->
      ctx.push(TimingScope(title, TimingScope.EventType.Other))
      ctx.push(BuildViewScope.forBuild(project, title))
      ctx.push(IdeaLogScope())

      try {
        ctx.pushJob { body.run(ctx) }
      } catch (e: ExecutionException) {
        IssueOutput.error(e.message ?: "Unknown error").withThrowable(e).submit(ctx)
        throw e
      }
    }
  }.asCompletableFuture()
}
