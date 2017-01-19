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
package com.google.idea.blaze.android.compatibility;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/** Compatibility facades for Android Studio 2.3. */
public class Compatibility {
  private Compatibility() {}

  /**
   * Facade for {@link org.jetbrains.android.sdk.AndroidSdkUtils} and {@link
   * com.android.tools.idea.sdk.AndroidSdks#getInstance()}.
   */
  public static class AndroidSdkUtils {
    private AndroidSdkUtils() {}

    public static Sdk findSuitableAndroidSdk(String targetHash) {
      return com.android.tools.idea.sdk.AndroidSdks.getInstance()
          .findSuitableAndroidSdk(targetHash);
    }

    public static List<Sdk> getAllAndroidSdks() {
      return com.android.tools.idea.sdk.AndroidSdks.getInstance().getAllAndroidSdks();
    }

    public static AndroidSdkAdditionalData getAndroidSdkAdditionalData(Sdk sdk) {
      return com.android.tools.idea.sdk.AndroidSdks.getInstance().getAndroidSdkAdditionalData(sdk);
    }
  }

  /**
   * Facade for {@link com.android.tools.idea.sdk.IdeSdks} and {@link
   * com.android.tools.idea.sdk.IdeSdks#getInstance()}.
   */
  public static class IdeSdks {
    private IdeSdks() {}

    public static File getAndroidSdkPath() {
      return com.android.tools.idea.sdk.IdeSdks.getInstance().getAndroidSdkPath();
    }

    public static List<Sdk> createAndroidSdkPerAndroidTarget(File androidSdkPath) {
      return com.android.tools.idea.sdk.IdeSdks.getInstance()
          .createAndroidSdkPerAndroidTarget(androidSdkPath);
    }
  }

  /**
   * Facade for {@link com.android.tools.idea.run.testing.AndroidTestListener} and {@link
   * com.android.tools.idea.testartifacts.instrumented.AndroidTestListener}
   */
  public static class AndroidTestListener
      extends com.android.tools.idea.testartifacts.instrumented.AndroidTestListener {
    public AndroidTestListener(LaunchStatus launchStatus, ConsolePrinter consolePrinter) {
      super(launchStatus, consolePrinter);
    }
  }

  /**
   * Facade for {@link com.android.tools.idea.run.testing.AndroidTestConsoleProperties} and {@link
   * com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties}
   */
  public static class AndroidTestConsoleProperties
      extends com.android.tools.idea.testartifacts.instrumented.AndroidTestConsoleProperties {
    public AndroidTestConsoleProperties(RunConfiguration runConfiguration, Executor executor) {
      super(runConfiguration, executor);
    }
  }

  /**
   * Facade for {@link com.android.tools.idea.run.testing.AndroidTestRunConfiguration} and {@link
   * com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration}
   */
  public static class AndroidTestRunConfiguration
      extends com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration {
    public AndroidTestRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
      super(project, configurationFactory);
    }
  }

  /** Facade for {@link com.android.tools.idea.run.tasks.ConnectDebuggerTask}. */
  public abstract static class ConnectDebuggerTask
      extends com.android.tools.idea.run.tasks.ConnectDebuggerTask {
    protected ConnectDebuggerTask(
        Set<String> applicationIds,
        AndroidDebugger<?> debugger,
        Project project,
        boolean monitorRemoteProcess) {
      super(applicationIds, debugger, project, monitorRemoteProcess);
    }
  }

  public static <S extends AndroidDebuggerState> DebugConnectorTask getConnectDebuggerTask(
      AndroidDebugger<S> androidDebugger,
      ExecutionEnvironment env,
      @Nullable AndroidVersion version,
      Set<String> applicationIds,
      AndroidFacet facet,
      S state,
      String runConfigTypeId,
      boolean monitorRemoteProcess) {
    return androidDebugger.getConnectDebuggerTask(
        env, version, applicationIds, facet, state, runConfigTypeId, monitorRemoteProcess);
  }

  public static void setFacetStateIsLibraryProject(JpsAndroidModuleProperties facetState) {
    facetState.PROJECT_TYPE = com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
  }
}
