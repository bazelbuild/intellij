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

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.errorprone.annotations.MustBeClosed
import com.google.idea.blaze.base.buildview.events.BuildEventParser
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep
import com.google.idea.blaze.base.command.buildresult.BuildResultParser
import com.google.idea.blaze.base.execution.BazelGuard
import com.google.idea.blaze.base.execution.ExecutionDeniedException
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.PrintOutput
import com.google.protobuf.CodedInputStream
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.LimitedInputStream
import com.intellij.util.system.OS
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.util.Optional
import kotlin.io.path.pathString
import kotlin.jvm.Throws
import kotlin.time.Duration.Companion.milliseconds

private val LOG: Logger = Logger.getInstance(BazelExecServiceImpl::class.java)

class BazelExecServiceImpl(private val project: Project, private val scope: CoroutineScope) : BazelExecService {

  @Throws(ExecutionException::class)
  private fun performGuardCheck() {
    try {
      BazelGuard.checkExtensionsIsExecutionAllowed(project)
    } catch (e: ExecutionDeniedException) {
      throw ExecutionException("Bazel execution denied: project is not trusted", e)
    }
  }

  private fun resolveBinaryPath(): String {
    return Optional.ofNullable(ProjectViewManager.getInstance(project).projectViewSet)
      .flatMap { it.getScalarValue(BazelBinarySection.KEY) }
      .map { it.path }
      .orElseGet { BlazeUserSettings.getInstance().bazelBinaryPath }
  }

  private fun assertNonBlocking() {
    LOG.assertTrue(
      !EDT.isCurrentThreadEdt(),
      "action would block UI thread",
    )
    LOG.assertTrue(
      !ApplicationManager.getApplication().isReadAccessAllowed,
      "action holds read lock, can block UI thread"
    )
  }

  private fun <T> executionScope(
    ctx: BlazeContext,
    block: suspend CoroutineScope.(BuildResultHelperBep) -> T
  ): T {
    return runBlocking {
      scope.async {
        ctx.pushJob {
          BuildResultHelperBep().use { block(it) }
        }
      }.await()
    }
  }

  @Throws(ExecutionException::class)
  private suspend fun execute(
    ctx: BlazeContext,
    cmdBuilder: BlazeCommand.Builder,
    usePty: Boolean,
    textAvailable: (String) -> Unit,
  ): Int {
    performGuardCheck()

    configureProxy(cmdBuilder)

    // the old sync view does not use a PTY based terminal, and idk why it does not work on windows :c
    if (usePty && BuildViewMigration.present(ctx) && OS.CURRENT != OS.Windows) {
      cmdBuilder.addBlazeFlags("--curses=yes")
    } else {
      cmdBuilder.addBlazeFlags("--curses=no")
    }

    val cmd = cmdBuilder.build()
    val root = cmd.workspaceRoot().orElseGet { WorkspaceRoot.fromProject(project).path() }

    val cmdLine = if (usePty) {
      val size = BuildViewScope.of(ctx)?.consoleSize ?: PtyConsoleView.DEFAULT_SIZE
      PtyCommandLine().withInitialColumns(size.columns).withInitialRows(size.rows)
    } else {
      GeneralCommandLine()
    }

    cmdLine
      .withExePath(resolveBinaryPath())
      .withParameters(cmd.toArgumentList())
      .withEnvironment(cmd.environment())
      .withWorkDirectory(root.pathString)

    var handler: OSProcessHandler? = null
    val exitCode = try {
      handler = withContext(Dispatchers.IO) {
        OSProcessHandler(cmdLine)
      }

      handler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          when (outputType) {
            ProcessOutputTypes.STDOUT -> textAvailable(event.text)
            ProcessOutputTypes.SYSTEM -> ctx.println(event.text)
            else -> ctx.output(PrintOutput.process(event.text))
          }

          LOG.debug("BAZEL OUTPUT: " + event.text)
        }
      })
      handler.startNotify()

      while (!handler.isProcessTerminated) {
        delay(100.milliseconds)
      }

      handler.exitCode ?: 1
    } finally {
      handler?.destroyProcess()
    }

    if (exitCode != 0) {
      ctx.setHasError()
    }

    return exitCode
  }

  private suspend fun parseEvent(ctx: BlazeContext, stream: BufferedInputStream) {
    // make sure that there are at least four bytes already available
    while (stream.available() < 4) {
      delay(10.milliseconds)
    }

    // protobuf messages are delimited by size (encoded as varint32),
    // read size manually to ensure the entire message is already available
    val size = CodedInputStream.readRawVarint32(stream.read(), stream)

    while (stream.available() < size) {
      delay(10.milliseconds)
    }

    val eventStream = LimitedInputStream(stream, size)
    val event = try {
      BuildEvent.parseFrom(eventStream)
    } catch (e: Exception) {
      LOG.error("could not parse event", e)

      // if the message could not be parsed, make sure to skip it
      if (eventStream.bytesRead < size) {
        stream.skip(size.toLong() - eventStream.bytesRead)
      }

      return
    }

    val issueReportingMode = BuildViewScope.of(ctx)?.issueReportingMode ?: IssueReportingMode.SYNC
    BuildEventParser.parse(event, issueReportingMode)?.let(ctx::output)
  }

  private fun CoroutineScope.parseEvents(ctx: BlazeContext, helper: BuildResultHelperBep): Job {
    return launch(Dispatchers.IO + CoroutineName("EventParser")) {
      try {
        // wait for bazel to create the output file
        while (!helper.outputFile.exists()) {
          delay(10.milliseconds)
        }

        FileInputStream(helper.outputFile).buffered().use { stream ->
          // keep reading events while the coroutine is active, i.e. bazel is still running,
          // or while the stream has data available (to ensure that all events are processed)
          while (isActive || stream.available() > 0) {
            parseEvent(ctx, stream)
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        LOG.error("error in event parser", e)
      }
    }
  }

  @Throws(ExecutionException::class)
  override fun build(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BlazeBuildOutputs {
    assertNonBlocking()
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.BUILD)

    return executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.buildFlags)

      val parseJob = parseEvents(ctx, provider)

      val exitCode = execute(ctx, cmdBuilder, usePty = true, ctx::println)
      val result = BuildResult.fromExitCode(exitCode)

      parseJob.cancelAndJoin()

      if (result.status == BuildResult.Status.FATAL_ERROR) {
        return@executionScope BlazeBuildOutputs.noOutputs(result)
      }

      provider.getBepStream(Optional.empty()).use { bepStream ->
        BlazeBuildOutputs.fromParsedBepOutput(
          BuildResultParser.getBuildOutput(bepStream, Interners.STRING),
        )
      }
    }
  }

  @MustBeClosed
  @Throws(ExecutionException::class)
  override fun exec(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): ExecResult {
    assertNonBlocking()
    LOG.assertTrue(cmdBuilder.name != BlazeCommandName.BUILD)

    val tempFile = Files.createTempFile("intellij-bazel-${cmdBuilder.name}-", ".stdout")

    val exitCode = executionScope(ctx) {
      Files.newOutputStream(tempFile).use { out ->
        execute(ctx, cmdBuilder, usePty = false) { text ->
          out.write(text.toByteArray())
        }
      }
    }

    return ExecResult(exitCode, tempFile)
  }
}
