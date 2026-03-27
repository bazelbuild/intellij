package com.google.idea.blaze.base.buildview

import com.google.errorprone.annotations.MustBeClosed
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.jvm.Throws

interface BazelExecService {

  companion object {

    @JvmStatic
    fun of(project: Project): BazelExecService = project.service()
  }

  /**
   * Runs a Bazel build and returns its result. Stderr and stdout are piped to the context for visibility as well as
   * BEP events are reported as context events.
   */
  @Throws(ExecutionException::class)
  fun build(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BlazeBuildOutputs

  /**
   * Runs a non-build Bazel command and returns the result. Stderr is piped to the context for visibility.
   * The caller must close the returned [ExecResult] to clean up temporary files.
   */
  @MustBeClosed
  @Throws(ExecutionException::class)
  fun exec(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): ExecResult
}
