/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.run.GradleTaskRunner;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;

public class BlazeInstantRunGradleTaskRunner implements GradleTaskRunner {
  private final Project project;
  private final BlazeContext context;
  private final File instantRunGradleBuildFile;

  public BlazeInstantRunGradleTaskRunner(Project project, BlazeContext context, File instantRunGradleBuildFile) {
    this.project = project;
    this.context = context;
    this.instantRunGradleBuildFile = instantRunGradleBuildFile;
  }

  @Override
  public boolean run(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
    throws InvocationTargetException, InterruptedException {
    assert !ApplicationManager.getApplication().isDispatchThread();

    final GradleInvoker gradleInvoker = GradleInvoker.getInstance(project);

    final AtomicBoolean success = new AtomicBoolean();
    final Semaphore done = new Semaphore();
    done.down();

    final GradleInvoker.AfterGradleInvocationTask afterTask = new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        success.set(result.isBuildSuccessful());
        gradleInvoker.removeAfterGradleInvocationTask(this);
        done.up();
      }
    };

    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, EXECUTE_TASK, project);
    List<String> jvmArguments = ImmutableList.of();

    // https://code.google.com/p/android/issues/detail?id=213040 - make split apks only available if an env var is set
    List<String> args = new ArrayList<>(commandLineArguments);
    if (!Boolean.valueOf(System.getenv(GradleTaskRunner.USE_SPLIT_APK))) {
      // force multi dex when the env var is not set to true
      args.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE, "MULTIDEX"));
    }

    // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use SwingUtilities.invokeAndWait. I tried
    // using Application.invokeAndWait but it never worked. IDEA also uses SwingUtilities in this scenario (see CompileStepBeforeRun.)
    SwingUtilities.invokeAndWait(() -> {
      gradleInvoker.addAfterGradleInvocationTask(afterTask);
      gradleInvoker.executeTasks(
        tasks,
        jvmArguments,
        args,
        taskId,
        new GradleNotificationListener(),
        instantRunGradleBuildFile,
        false,
        true
      );
    });

    done.waitFor();
    return success.get();
  }

  class GradleNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
      super.onTaskOutput(id, text, stdOut);
      String toPrint = text.trim();
      if (!Strings.isNullOrEmpty(toPrint)) {
        context.output(new PrintOutput(toPrint, stdOut ? PrintOutput.OutputType.NORMAL : PrintOutput.OutputType.ERROR));
      }
    }
  }
}
