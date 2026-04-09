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
