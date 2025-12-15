package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.clwb.run.BlazeCidrRemoteDebugProcess;
import com.google.idea.blaze.common.Label;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.util.system.OS;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExecutionTest extends ClwbHeadlessTestCase {

  // catch requires bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  private static final String ECHO_OUTPUT_MARKER = "ECHO_OUTPUT_FILE: ";

  @Test
  public void testClwb() throws Exception {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkRun(DefaultRunExecutor.EXECUTOR_ID);
    checkArgs(DefaultRunExecutor.EXECUTOR_ID);

    // debugging on macOS requires special permissions which the CI agents do not have
    if (!OS.CURRENT.equals(OS.macOS)) {
      checkRun(DefaultDebugExecutor.EXECUTOR_ID);
      checkArgs(DefaultDebugExecutor.EXECUTOR_ID);
    }
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);

    // required for building with bazel 6
    if (OS.CURRENT.equals(OS.Windows)) {
      builder.addBuildFlag("--cxxopt=/std:c++17");
    } else {
      builder.addBuildFlag("--cxxopt=-std=c++17");
    }

    return builder;
  }

  private void checkRun(String executorId) throws Exception {
    final var echo = execute(Label.of("//main:echo0"), executorId, "");
    echo.assertSuccess();

    final var gtest = execute(Label.of("//main:gtest"), executorId, "");
    gtest.assertSuccess();

    final var catch2 = execute(Label.of("//main:catch"), executorId, "");
    catch2.assertSuccess();
  }

  private void checkArgs(String executorId) throws Exception {
    assertThat(executeEcho("echo0", executorId, "'one argument with spaces'")).containsExactly(
        "one argument with spaces"
    );

    assertThat(executeEcho("echo0", executorId, "'one argument' 'another argument'")).containsExactly(
        "one argument",
        "another argument"
    );

    assertThat(executeEcho("echo1", executorId, "CONFIG_ARGUMENT")).containsExactly(
        "BUILD_FILE_STRING",
        "CONFIG_ARGUMENT"
    );

    assertThat(executeEcho("echo2", executorId, "CONFIG_ARGUMENT")).containsExactly(
        "main/echo.cc",
        "CONFIG_ARGUMENT"
    );
  }

  /**
   * Executes the echo program and returns the program's output lines. The
   * program simply writes all received arguments to a file.
   */
  private List<String> executeEcho(String target, String executorId, String args) throws Exception {
    final var result = execute(Label.of("//main:" + target), executorId, args);
    result.assertSuccess();

    final var line = result.output().lines().filter((it) -> it.startsWith(ECHO_OUTPUT_MARKER)).findFirst();
    assertThat(line).isPresent();

    final var path = Path.of(line.get().substring(ECHO_OUTPUT_MARKER.length()));
    assertThat(Files.exists(path)).isTrue();

    return Files.readAllLines(path);
  }

  /**
   * Executes the run configuration for the given label with the given executor.
   * The flags are set on the run configuration. Supported executors:
   * - DefaultRunExecutor: just runs the binary or test
   * - DefaultDebugExecutor: debugs the binary or test
   */
  private ExecutionResult execute(Label label, String executorId, String args) throws Exception {
    final var element = findRule(label);

    final var context = ConfigurationContext.getFromContext(SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
        .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
        .add(BlazeCommandRunConfigurationCommonState.USER_EXE_FLAG, new String[]{args})
        .build(), ActionPlaces.UNKNOWN);

    final var executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    assertThat(executor).isNotNull();

    final var producer = RunConfigurationProducer.getInstance(BlazeBuildFileRunConfigurationProducer.class);
    final var configuration = producer.createConfigurationFromContext(context);
    assertThat(configuration).isNotNull();

    final var environmentBuilder = ExecutionUtil.createEnvironment(executor, configuration.getConfigurationSettings());
    assertThat(environmentBuilder).isNotNull();

    final var manager = (ExecutionManagerImpl) ExecutionManager.getInstance(getProject());
    manager.setForceCompilationInTests(true);
    manager.restartRunProfile(environmentBuilder.build());

    final var future = new CompletableFuture<Integer>();
    final var outputBuilder = new StringBuilder();

    final var listener = new ProcessListener() {
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          outputBuilder.append(event.getText());
        }
      }
    };

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        if (executorId.equals(DefaultRunExecutor.EXECUTOR_ID)) {
          handler.addProcessListener(listener);
        }

        if (executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)) {
          final var session = XDebuggerManager.getInstance(myProject).getCurrentSession();
          assertThat(session).isNotNull();

          if (session.getDebugProcess() instanceof BlazeCidrRemoteDebugProcess process) {
            process.getTargetProcess().addProcessListener(listener);
          } else if (session.getDebugProcess() instanceof CidrLocalDebugProcess process) {
            process.getProcessHandler().addProcessListener(listener);
          } else {
            future.completeExceptionally(new IllegalStateException("unexpected debug process type"));
          }
        }
      }

      @Override
      public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, Throwable cause) {
        future.completeExceptionally(cause);
      }

      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
        future.complete(exitCode);
      }
    });

    final var exitCode = pullFuture(future, 2, TimeUnit.MINUTES);
    return new ExecutionResult(outputBuilder.toString(), exitCode == null ? -1 : exitCode);
  }

  private record ExecutionResult(String output, int exitCode) {

    void assertSuccess() {
      assertWithMessage(output).that(exitCode).isEqualTo(0);
    }
  }
}
