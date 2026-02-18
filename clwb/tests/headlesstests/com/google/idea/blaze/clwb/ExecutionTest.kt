package com.google.idea.blaze.clwb

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.idea.blaze.base.bazel.BazelVersion
import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase
import com.google.idea.blaze.clwb.run.BlazeCidrRemoteDebugProcess
import com.google.idea.blaze.common.Label
import com.google.idea.testing.headless.BazelVersionRule
import com.google.idea.testing.headless.ProjectViewBuilder
import com.intellij.execution.*
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.system.OS
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val ECHO_OUTPUT_MARKER = "ECHO_OUTPUT_FILE: "

@RunWith(JUnit4::class)
class ExecutionTest : ClwbHeadlessTestCase() {

  @Rule
  @JvmField
  val bazelRule = BazelVersionRule(7, 0)

  @Test
  fun testClwb() {
    val errors = runSync(defaultSyncParams().build())
    errors.assertNoErrors()

    checkRun(DefaultRunExecutor.EXECUTOR_ID)
    checkArgs(DefaultRunExecutor.EXECUTOR_ID)
    checkTest(DefaultRunExecutor.EXECUTOR_ID)

    // debugging on macOS requires special permissions which the ci agents do not have
    if (OS.CURRENT != OS.macOS) {
      checkRun(DefaultDebugExecutor.EXECUTOR_ID)
      checkArgs(DefaultDebugExecutor.EXECUTOR_ID)
      checkTest(DefaultDebugExecutor.EXECUTOR_ID)
    }
  }

  override fun projectViewText(version: BazelVersion): ProjectViewBuilder {
    val builder = super.projectViewText(version)

    // Required for building with bazel 6
    if (OS.CURRENT == OS.Windows) {
      builder.addBuildFlag("--cxxopt=/std:c++17")
    } else {
      builder.addBuildFlag("--cxxopt=-std=c++17")
    }

    return builder
  }

  private fun checkRun(executorId: String) {
    val echo = execute(Label.of("//main:echo0"), executorId)
    echo.assertSuccess()

    val gtest = execute(Label.of("//main:gtest"), executorId)
    gtest.assertSuccess()

    val catch2 = execute(Label.of("//main:catch"), executorId)
    catch2.assertSuccess()
  }

  private fun checkArgs(executorId: String) {
    assertThat(executeEcho("echo0", executorId, "'one argument with spaces'")).containsExactly(
      "one argument with spaces"
    )

    assertThat(executeEcho("echo0", executorId, "'one argument' 'another argument'")).containsExactly(
      "one argument",
      "another argument"
    )

    assertThat(executeEcho("echo1", executorId, "CONFIG_ARGUMENT")).containsExactly(
      "BUILD_FILE_STRING",
      "CONFIG_ARGUMENT"
    )

    assertThat(executeEcho("echo2", executorId, "CONFIG_ARGUMENT")).containsExactly(
      "main/echo.cc",
      "CONFIG_ARGUMENT"
    )
  }

  fun checkTest(executorId: String) {
    // filter gtest by suite name
    val gtestFiltered = execute(
      Label.of("//main:gtest"), executorId,
      flags = listOf("--test_output=streamed", "--test_filter=FilterSuite.*")
    )
    gtestFiltered.assertSuccess()
    assertThat(gtestFiltered.output).contains("FilterSuite.FilteredTest")
    assertThat(gtestFiltered.output).contains("FilterSuite.SkippedTest")
    assertThat(gtestFiltered.output).doesNotContain("SampleSuite.SampleTest")
    assertThat(gtestFiltered.output).doesNotContain("SampleSuite.AnotherTest")

    // filter gtest by specific test
    val gtestSingleTest = execute(
      Label.of("//main:gtest"), executorId,
      flags = listOf("--test_output=streamed", "--test_filter=FilterSuite.FilteredTest")
    )
    gtestSingleTest.assertSuccess()
    assertThat(gtestSingleTest.output).contains("FilterSuite.FilteredTest")
    assertThat(gtestSingleTest.output).doesNotContain("FilterSuite.SkippedTest")

    // filter catch2 tests
    val catchFiltered = execute(
      Label.of("//main:catch"), executorId,
      flags = listOf("--test_output=streamed", "--test_filter=FilteredTest")
    )
    catchFiltered.assertSuccess()
    assertThat(catchFiltered.output).contains("FilteredTest")
    assertThat(catchFiltered.output).doesNotContain("SkippedTest")
    assertThat(catchFiltered.output).doesNotContain("Test0")
  }

  private fun executeEcho(target: String, executorId: String, args: String): List<String> {
    val result = execute(Label.of("//main:$target"), executorId, args = listOf(args))
    result.assertSuccess()

    val line = result.output.lines().firstOrNull { it.startsWith(ECHO_OUTPUT_MARKER) }
    requireNotNull(line)

    val path = Path.of(line.substring(ECHO_OUTPUT_MARKER.length))
    assertThat(Files.exists(path)).isTrue()

    return Files.readAllLines(path)
  }

  private fun execute(
    label: Label,
    executorId: String,
    flags: List<String> = emptyList(),
    args: List<String> = emptyList()
  ): ExecutionResult {
    val element = findRule(label)

    val dataContextBuilder = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
      .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
      .add(BlazeCommandRunConfigurationCommonState.USER_BLAZE_FLAG, flags.toTypedArray())
      .add(BlazeCommandRunConfigurationCommonState.USER_EXE_FLAG, args.toTypedArray())

    val context = ConfigurationContext.getFromContext(
      dataContextBuilder.build(),
      ActionPlaces.UNKNOWN
    )

    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)
    requireNotNull(executor) { "executor not found: $executorId" }

    val producer = RunConfigurationProducer.getInstance(BlazeBuildFileRunConfigurationProducer::class.java)
    val configuration = producer.createConfigurationFromContext(context)
    requireNotNull(configuration)

    val environmentBuilder = ExecutionUtil.createEnvironment(executor, configuration.configurationSettings)
    requireNotNull(environmentBuilder)

    val manager = ExecutionManager.getInstance(project) as ExecutionManagerImpl
    manager.forceCompilationInTests = true
    manager.restartRunProfile(environmentBuilder.build())

    val future = CompletableFuture<Int>()
    val outputBuilder = StringBuilder()

    val listener = object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          outputBuilder.append(event.text)
        }
      }
    }

    project.messageBus.connect(testRootDisposable).subscribe(
      ExecutionManager.EXECUTION_TOPIC,
      ExecutionResultListener(myProject, listener, future)
    )

    val exitCode = pullFuture(future, 2, TimeUnit.MINUTES)
    return ExecutionResult(outputBuilder.toString(), exitCode ?: -1)
  }

  private data class ExecutionResult(val output: String, val exitCode: Int) {

    fun assertSuccess() {
      assertWithMessage(output).that(exitCode).isEqualTo(0)
    }
  }
}

private class ExecutionResultListener(
  private val project: Project,
  private val listener: ProcessListener,
  private val future: CompletableFuture<Int>
) : ExecutionListener {

  override fun processStarted(executor: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    if (executor == DefaultRunExecutor.EXECUTOR_ID) {
      handler.addProcessListener(listener)
    }

    if (executor == DefaultDebugExecutor.EXECUTOR_ID) {
      val session = XDebuggerManager.getInstance(project).currentSession
      assertThat(session).isNotNull()

      when (val debugProcess = session?.debugProcess) {
        is BlazeCidrRemoteDebugProcess -> debugProcess.targetProcess.addProcessListener(listener)
        is CidrLocalDebugProcess -> debugProcess.processHandler.addProcessListener(listener)
        else -> future.completeExceptionally(IllegalStateException("unexpected debug process type"))
      }
    }
  }

  override fun processNotStarted(executor: String, env: ExecutionEnvironment, cause: Throwable) {
    future.completeExceptionally(cause)
  }

  override fun processTerminated(executor: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
    future.complete(exitCode)
  }
}