package com.google.idea.blaze.base.buildview

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.buildview.events.BuildEventParser
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.sync.aspects.BuildResult
import com.google.protobuf.CodedInputStream
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.LimitedInputStream
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.FileInputStream
import kotlin.io.path.pathString

private val LOG: Logger = Logger.getInstance(BazelService::class.java)

@Service(Service.Level.PROJECT)
class BazelService(private val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun instance(project: Project): BazelService = project.service()
  }

  // #api223 use the injected scope
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun dispose() {
    scope.cancel()
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
    block: suspend CoroutineScope.(BuildResultHelper) -> T
  ): T {
    return ctx.pushJob(scope) {
      BuildResultHelper().use { block(it) }
    }
  }

  private suspend fun execute(ctx: BlazeContext, cmd: BlazeCommand): Int {
    val root = cmd.effectiveWorkspaceRoot.orElseGet { WorkspaceRoot.fromProject(project).path() }

    val handler = GeneralCommandLine()
      .withExePath(cmd.binaryPath)
      .withParameters(cmd.toArgumentList())
      .apply { setWorkDirectory(root.pathString) } // required for backwards compatability
      .withRedirectErrorStream(true)
      .let(::OSProcessHandler)

    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        ctx.println(event.text.trimEnd())
      }
    })
    handler.startNotify()

    try {
      while (!handler.isProcessTerminated) delay(100)
    } finally {
      handler.destroyProcess()
    }

    val exitCode = handler.exitCode ?: 1
    if (exitCode != 0) {
      ctx.setHasError()
    }

    return exitCode
  }

  private suspend fun parseEvent(ctx: BlazeContext, stream: BufferedInputStream) {
    // make sure that there are at least four bytes already available
    while (stream.available() < 4) delay(10)

    // protobuf messages are delimited by size (encoded as varint32),
    // read size manually to ensure the entire message is already available
    val size = CodedInputStream.readRawVarint32(stream.read(), stream)
    while (stream.available() < size) delay(10)

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

    if (event == null) {
      delay(10)
    } else {
      BuildEventParser.parse(event)?.let(ctx::output)
    }
  }

  private fun CoroutineScope.parseEvents(ctx: BlazeContext, helper: BuildResultHelper): Job {
    return launch(CoroutineName("EventParser")) {
      try {
        // wait for bazel to create the output file
        while (!helper.outputFile.exists()) delay(10)

        FileInputStream(helper.outputFile).buffered().use { stream ->
          // keep reading events while the coroutine is active, i.e. bazel is still running,
          // or while the stream has data available (to ensure that all events are processed)
          while (isActive || stream.available() > 0) {
            parseEvent(ctx, stream)
          }
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error("error in event parser", e)
      }
    }
  }

  fun build(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BlazeBuildOutputs {
    assertNonBlocking()
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.BUILD)

    return executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.getBuildFlags())

      val parseJob = parseEvents(ctx, provider)

      val exitCode = execute(ctx, cmdBuilder.build())
      val result = BuildResult.fromExitCode(exitCode)

      parseJob.cancelAndJoin()

      if (result.status != BuildResult.Status.SUCCESS) {
        BlazeBuildOutputs.noOutputs(result)
      } else {
        BlazeBuildOutputs.fromParsedBepOutput(result, provider.getBuildOutput())
      }
    }
  }
}

