/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.run.debug;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.run.BlazeJavaDebuggerRunner;
import com.google.idea.blaze.java.run.BlazeJavaRunProfileState;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection;

/**
 * A runner that extends {@code BlazeJavaDebuggerRunner} to work with Kotlin projects run
 * configurations with Bazel.
 *
 * <p>This class is mainly needed to view coroutines debugging panel and enable coroutines plugin
 * for Kotlin targets that use coroutines and depend on the required versions of kotlinx-coroutines
 * library.
 */
public class KotlinDebuggerRunner extends BlazeJavaDebuggerRunner {
  // Experiment supporting Kotlin coroutines debugging
  private static final BoolExperiment coroutinesDebuggingEnabled =
      new BoolExperiment("kotlin.coroutinesDebugging.enabled", false);

  @Override
  @Nullable
  public RunContentDescriptor createContentDescriptor(
      RunProfileState state, ExecutionEnvironment env) throws ExecutionException {

    if (coroutinesDebuggingEnabled.getValue() && state instanceof BlazeJavaRunProfileState) {
      // If the kotlinx-coroutines library is a transitive dependency of the target to debug, save
      // the path of the library to be used as a javaagent during bazel run and create a
      // DebuggerConnection object to show the coroutines panel during debugging
      BlazeCommandRunConfiguration config =
          BlazeCommandRunConfigurationRunner.getConfiguration(env);
      Optional<ArtifactLocation> libArtifact =
          KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

      libArtifact
          .flatMap(artifact -> getArtifactAbsolutePath(config, artifact))
          .ifPresent(path -> attachCoroutinesPanel((BlazeJavaRunProfileState) state, path, env));
    }

    return super.createContentDescriptor(state, env);
  }

  private static void attachCoroutinesPanel(
      BlazeJavaRunProfileState state, String libAbsolutePath, ExecutionEnvironment env) {
    state.addKotlinxCoroutinesJavaAgent(libAbsolutePath);

    var unused =
        new DebuggerConnection(
            env.getProject(),
            /*configuration=*/ null,
            new JavaParameters(),
            /*modifyArgs=*/ false,
            /*alwaysShowPanel=*/ true);
  }

  private static Optional<String> getArtifactAbsolutePath(
      BlazeCommandRunConfiguration config, ArtifactLocation libArtifact) {
    Project project = config.getProject();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      notify("Cannot view coroutines debugging panel: project needs to be synced.");
      return Optional.empty();
    }

    File libFile =
        JarCache.getInstance(project)
            .getCachedSourceJar(blazeProjectData.getArtifactLocationDecoder(), libArtifact);
    if (libFile == null) {
      notify(
          String.format(
              "Cannot view coroutines debugging panel: %s jar file cannot be found.",
              libArtifact.getRelativePath()));
      return Optional.empty();
    }

    return Optional.of(libFile.getAbsolutePath());
  }

  private static void notify(String content) {
    Notifications.Bus.notify(
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KotlinDebuggerNotification")
            .createNotification(content, NotificationType.INFORMATION));
  }
}
