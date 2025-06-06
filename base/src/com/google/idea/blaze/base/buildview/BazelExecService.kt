package com.google.idea.blaze.base.buildview

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.buildview.events.BuildEventParser
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep
import com.google.idea.blaze.base.command.buildresult.BuildResultParser
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.PrintOutput
import com.google.protobuf.CodedInputStream
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.LimitedInputStream
import com.intellij.util.system.OS
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.Optional
import kotlin.io.path.pathString

private val LOG: Logger = Logger.getInstance(BazelExecService::class.java)

@Service(Service.Level.PROJECT)
class BazelExecService(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun instance(project: Project): BazelExecService = project.service()
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

  private suspend fun execute(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): Int {
    // the old sync view does not use a PTY based terminal, and idk why it does not work on windows :c
    if (BuildViewMigration.present(ctx) && OS.CURRENT != OS.Windows) {
      cmdBuilder.addBlazeFlags("--curses=yes")
    } else {
      cmdBuilder.addBlazeFlags("--curses=no")
    }

    val cmd = cmdBuilder.build()
    val root = cmd.effectiveWorkspaceRoot.orElseGet { WorkspaceRoot.fromProject(project).path() }
    val size = BuildViewScope.of(ctx)?.consoleSize ?: PtyConsoleView.DEFAULT_SIZE

    val cmdLine = PtyCommandLine()
      .withInitialColumns(size.columns)
      .withInitialRows(size.rows)
      .withExePath(cmd.binaryPath)
      .withParameters(cmd.toArgumentList())
      .withWorkDirectory(root.pathString)

    var handler: OSProcessHandler? = null
    val exitCode = try {
      handler = withContext(Dispatchers.IO) {
        OSProcessHandler(cmdLine)
      }

      handler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          if (outputType == ProcessOutputTypes.SYSTEM) {
            ctx.println(event.text)
          } else {
            ctx.output(PrintOutput.process(event.text))
          }

          LOG.debug("BUILD OUTPUT: " + event.text)
        }
      })
      handler.startNotify()

      while (!handler.isProcessTerminated) {
        delay(100)
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
      delay(10)
    }

    // protobuf messages are delimited by size (encoded as varint32),
    // read size manually to ensure the entire message is already available
    val size = CodedInputStream.readRawVarint32(stream.read(), stream)

    while (stream.available() < size) {
      delay(10)
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

    BuildEventParser.parse(event)?.let(ctx::output)
  }

  private fun CoroutineScope.parseEvents(ctx: BlazeContext, helper: BuildResultHelperBep): Job {
    return launch(Dispatchers.IO + CoroutineName("EventParser")) {
      try {
        // wait for bazel to create the output file
        while (!helper.outputFile.exists()) {
          delay(10)
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

  fun build(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BlazeBuildOutputs.Legacy {
    assertNonBlocking()
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.BUILD)

    return executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.buildFlags)

      val parseJob = parseEvents(ctx, provider)

      val exitCode = execute(ctx, cmdBuilder)
      val result = BuildResult.fromExitCode(exitCode)

      parseJob.cancelAndJoin()

      if (result.status == BuildResult.Status.FATAL_ERROR) {
        return@executionScope BlazeBuildOutputs.noOutputsForLegacy(result)
      }

      provider.getBepStream(Optional.empty()).use { bepStream ->
        BlazeBuildOutputs.fromParsedBepOutputForLegacy(
          BuildResultParser.getBuildOutputForLegacySync(bepStream, Interners.STRING),
        )
      }
    }
  }
}