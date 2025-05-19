package com.google.idea.blaze.base.buildview

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.buildview.events.BuildEventParser
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep
import com.google.idea.blaze.base.command.buildresult.BuildResultParser
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.PrintOutput
import com.google.protobuf.CodedInputStream
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.*
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
import kotlinx.coroutines.future.asCompletableFuture
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.*
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

  @Throws(IOException::class)
  private fun <T> executionScope(
    ctx: BlazeContext,
    block: suspend CoroutineScope.(BuildResultHelperBep) -> BazelProcess<T>
  ): BazelProcess<T> {
    // create the ProcessHandler and thereby start the bazel process
    val deferred = scope.async(CoroutineName("BazelExecution")) {
      BuildResultHelperBep().use { block(it) }
    }
    ctx.addCancellationHandler { deferred.cancel() }

    // get the BazelProcess, but cancel it if the scope is canceled
    val process = try {
      deferred.asCompletableFuture().get()
    } catch (e: Exception) {
      throw IOException("bazel process creation was interrupted", e)
    }
    ctx.addCancellationHandler { process.cancel() }

    return process
  }

  /**
   * Executes the bazel command and creates a [BazelProcess] that captures the
   * running process. Returns immediately after the process was created. The
   * result of the process is awaited asynchronously and can be accessed as by
   * the deferred result in the [BazelProcess].
   *
   * The [computeResult] argument allows creating a custom result upon completion
   * of the process. It cannot be a suspending function to avoid race conditions.
   */
  private suspend fun <T> execute(
    ctx: BlazeContext,
    cmdBuilder: BlazeCommand.Builder,
    useCurses: Boolean,
    forwardOutput: Boolean,
    computeResult: (Int) -> T,
  ): BazelProcess<T> {
    if (useCurses) {
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
      // redirect the error stream to not print everything in red :c
      .withRedirectErrorStream(true)
      .withExePath(cmd.binaryPath)
      .withParameters(cmd.toArgumentList())
      .withWorkDirectory(root.pathString)

    val handler = withContext(Dispatchers.IO) {
      ColoredProcessHandler(cmdLine)
    }

    // handle process termination and forward the result
    val result = CompletableDeferred<T>()
    handler.addProcessListener(object : ProcessListener {
      override fun processNotStarted() {
        result.completeExceptionally(IOException("process not started"))
      }

      override fun processTerminated(event: ProcessEvent) {
        result.complete(computeResult(event.exitCode))

        if (event.exitCode != 0) {
          ctx.setHasError()
        }
      }
    })

    // add a listener to forward the output to the context if requested
    if (forwardOutput) {
      handler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          if (outputType == ProcessOutputTypes.SYSTEM) {
            ctx.println(event.text)
          } else {
            ctx.output(PrintOutput.process(event.text))
          }

          // used for debugging headless tests
          LOG.debug("BAZEL OUTPUT: " + event.text)
        }
      })
    }

    return BazelProcess(handler, result)
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

  private fun parseEvents(ctx: BlazeContext, helper: BuildResultHelperBep): Job {
    return scope.launch(Dispatchers.IO + CoroutineName("EventParser")) {
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

  /**
   * Runs a bazel build, output is added to the [BlazeContext]. If the context
   * contains a [BuildViewScope] the bazel is run with curses.
   *
   * Automatically calls startNotify for the process, output should be retrieved
   * from the context.
   */
  @Throws(IOException::class)
  fun build(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BazelProcess<BlazeBuildOutputs.Legacy> {
    assertNonBlocking()
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.BUILD)

    // the old sync view does not use a PTY based terminal, and idk why it does not work on windows :c
    val useCurses = BuildViewMigration.present(ctx) && OS.CURRENT != OS.Windows

    val process = executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.buildFlags)

      val parseJob = parseEvents(ctx, provider)

      execute(ctx, cmdBuilder, useCurses, forwardOutput = true) { exitCode ->
        runBlocking { parseJob.cancelAndJoin() }

        val result = BuildResult.fromExitCode(exitCode)
        if (result.status == BuildResult.Status.FATAL_ERROR) {
          return@execute BlazeBuildOutputs.noOutputsForLegacy(result)
        }

        provider.getBepStream(Optional.empty()).use { bepStream ->
          BlazeBuildOutputs.fromParsedBepOutputForLegacy(
            BuildResultParser.getBuildOutputForLegacySync(bepStream, Interners.STRING),
          )
        }
      }
    }

    // automatically call start notify for build processes, since the output is forwarded to the context
    process.hdl.startNotify()

    return process
  }

  /**
   * Runs a bazel test, output is added to the [BlazeContext]. Bazel is always
   * run without curses.
   *
   * Does not call startNotify for the process, callee is required to set up
   * output handling themselves.
   */
  @Throws(IOException::class)
  fun test(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder, resultHolder: BlazeTestResultHolder): BazelProcess<Unit> {
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.TEST)

    return executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.buildFlags)

      execute(ctx, cmdBuilder, useCurses = false, forwardOutput = false) {
        val result = provider.getBepStream(Optional.empty()).use(BuildResultParser::getTestResults)
        resultHolder.setTestResults(result)
      }
    }
  }

  /**
   * Runs a bazel run command, output is added to the [BlazeContext]. Bazel is
   * always run without curses.
   *
   * Does not call startNotify for the process, callee is required to set up
   * output handling themselves.
   */
  @Throws(IOException::class)
  fun run(ctx: BlazeContext, cmdBuilder: BlazeCommand.Builder): BazelProcess<Unit> {
    LOG.assertTrue(cmdBuilder.name == BlazeCommandName.RUN)

    return executionScope(ctx) { provider ->
      cmdBuilder.addBlazeFlags(provider.buildFlags)
      execute(ctx, cmdBuilder, useCurses = false, forwardOutput = false) { }
    }
  }
}
