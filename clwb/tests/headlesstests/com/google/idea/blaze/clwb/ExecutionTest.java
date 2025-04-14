package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.common.Label;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExecutionTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() throws Exception {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkRun();
    // checkTest(); -- java.lang.IllegalArgumentException: Not a valid label, no target name found
  }

  private void checkRun() throws Exception {
    final var result = execute(Label.of("//main:hello-world"), DefaultRunExecutor.EXECUTOR_ID);
    assertThat(result).isEqualTo(0);
  }

  private void checkTest() throws Exception {
    final var result = execute(Label.of("//main:test"), DefaultRunExecutor.EXECUTOR_ID);
    assertThat(result).isEqualTo(0);
  }

  private int execute(Label label, String executorId) throws Exception {
    final var element = findRule(label);

    final var context = ConfigurationContext.getFromContext(SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
        .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
        .build(), ActionPlaces.UNKNOWN);

    final var executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    assertThat(executor).isNotNull();

    final var producer = RunConfigurationProducer.getInstance(BlazeBuildFileRunConfigurationProducer.class);
    final var configuration = producer.createConfigurationFromContext(context);
    assertThat(configuration).isNotNull();

    final var environmentBuilder = ExecutionUtil.createEnvironment(executor, configuration.getConfigurationSettings());
    assertThat(environmentBuilder).isNotNull();

    final var environment = environmentBuilder.build();

    final var future = new CompletableFuture<Integer>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, Throwable cause) {
        future.completeExceptionally(cause);
      }

      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
        future.complete(exitCode);
      }
    });

    final var manager = (ExecutionManagerImpl) ExecutionManager.getInstance(getProject());
    manager.setForceCompilationInTests(true);
    manager.restartRunProfile(environment);

    return pullFuture(future, 30, TimeUnit.SECONDS);
  }
}
