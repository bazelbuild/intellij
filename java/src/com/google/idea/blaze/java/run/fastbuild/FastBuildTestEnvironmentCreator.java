/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.run.BlazeJavaRunConfigState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

abstract class FastBuildTestEnvironmentCreator implements BuildSystemExtensionPoint {

  private static final String OUTPUT_FILE_VARIABLE = "XML_OUTPUT_FILE";
  private static final String RUNFILES_DIR_VARIABLE = "TEST_SRCDIR";
  private static final String TEST_BINARY_VARIABLE = "TEST_BINARY";
  private static final String TARGET_VARIABLE = "TEST_TARGET";
  private static final String TEMP_DIRECTORY_VARIABLE = "TEST_TMPDIR";
  private static final String TEST_FILTER_VARIABLE = "TESTBRIDGE_TEST_ONLY";
  private static final String WORKSPACE_VARIABLE = "TEST_WORKSPACE";

  private static final ExtensionPointName<FastBuildTestEnvironmentCreator> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FastBuildTestEnvironmentCreator");

  static FastBuildTestEnvironmentCreator getInstance(BuildSystemName buildSystemName) {
    return BuildSystemExtensionPoint.getInstance(EP_NAME, buildSystemName);
  }

  abstract String getTestClassProperty();

  abstract String getTestRunner();

  abstract File getJavaBinFromLauncher(
      Project project,
      Label label,
      @Nullable Label javaLauncher,
      boolean swigdeps,
      String runfilesDir);

  GeneralCommandLine createCommandLine(
      Project project,
      BlazeCommandRunConfiguration config,
      FastBuildInfo fastBuildInfo,
      File outputFile,
      @Nullable String testFilter,
      int debugPort)
      throws ExecutionException {

    Label target = fastBuildInfo.label();
    FastBuildBlazeData targetData = fastBuildInfo.blazeData().get(target);
    checkState(targetData != null, "Couldn't find blaze data for %s", target);
    checkState(targetData.javaInfo().isPresent(), "Couldn't find Java info for %s", target);
    JavaInfo targetJavaInfo = targetData.javaInfo().get();

    // To emulate 'blaze test', the binary should be launched from something like
    // blaze-out/k8-opt/bin/path/to/package/MyLabel.runfiles/io_bazel
    String workspaceName = targetData.workspaceName();
    Path runfilesDir =
        Paths.get(fastBuildInfo.deployJar().getParent(), target.targetName() + ".runfiles");
    Path workingDir = runfilesDir.resolve(workspaceName);

    JavaCommandBuilder commandBuilder = new JavaCommandBuilder();
    commandBuilder.setWorkingDirectory(workingDir.toFile());

    commandBuilder.setJavaBinary(
        getJavaBinFromLauncher(
            project,
            target,
            getLauncher(fastBuildInfo).orElse(null),
            getSwigdeps(fastBuildInfo),
            runfilesDir.toString()));

    fastBuildInfo.classpath().forEach(commandBuilder::addClasspathElement);

    commandBuilder.addSystemProperty(
        getTestClassProperty(),
        FastBuildTestClassFinder.getInstance(project).getTestClass(target, targetJavaInfo));

    if (targetJavaInfo.mainClass().isPresent() && !targetJavaInfo.mainClass().get().isBlank()) {
        commandBuilder.setMainClass(targetJavaInfo.mainClass().get());
    } else {
        commandBuilder.setMainClass(getTestRunner());
    }

    commandBuilder
        .addEnvironmentVariable(TEST_BINARY_VARIABLE, getTestBinary(target))
        .addEnvironmentVariable(OUTPUT_FILE_VARIABLE, outputFile.getAbsolutePath())
        .addEnvironmentVariable("GUNIT_OUTPUT", "xml:" + outputFile.getAbsolutePath())
        .addEnvironmentVariable(RUNFILES_DIR_VARIABLE, runfilesDir.toString())
        .addEnvironmentVariable(TARGET_VARIABLE, target.toString())
        .addEnvironmentVariable("USER", SystemProperties.getUserName())
        .addEnvironmentVariable(WORKSPACE_VARIABLE, workspaceName);
    addTestSizeVariables(commandBuilder, targetJavaInfo);
    configureTestOutputs(project, commandBuilder, target, fastBuildInfo.blazeInfo());

    String tmpdir = System.getProperty("java.io.tmpdir");
    commandBuilder
        .addEnvironmentVariable(TEMP_DIRECTORY_VARIABLE, tmpdir)
        .addEnvironmentVariable("HOME", tmpdir);

    if (testFilter != null) {
      commandBuilder.addEnvironmentVariable(TEST_FILTER_VARIABLE, testFilter);
    }

    for (FastBuildTestEnvironmentModifier modifier :
        FastBuildTestEnvironmentModifier.getModifiers(Blaze.getBuildSystemName(project))) {
      modifier.modify(
          commandBuilder, config.getTargetKind(), fastBuildInfo, fastBuildInfo.blazeInfo());
    }

    // add custom jvm flags last (blaze stomps all over custom environment variables)
    if (debugPort > 0) {
      commandBuilder.addJvmArgument(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    addJvmOptsFromBlazeFlags(config, commandBuilder);

    for (String flag : targetJavaInfo.jvmFlags()) {
      commandBuilder.addJvmArgument(
          LocationSubstitution.replaceLocations(flag, target, targetData.data()));
    }

    return commandBuilder.build();
  }

  private Optional<Label> getLauncher(FastBuildInfo fastBuildInfo) {
    Label label = fastBuildInfo.label();
    FastBuildBlazeData targetData = fastBuildInfo.blazeData().get(label);
    checkState(targetData != null, "Couldn't find data for target %s", label);
    checkState(targetData.javaInfo().isPresent(), "Couldn't find Java info for target %s", label);
    JavaInfo javaInfo = targetData.javaInfo().get();
    return javaInfo.launcher();
  }

  private static boolean getSwigdeps(FastBuildInfo fastBuildInfo) {
    Label label = fastBuildInfo.label();
    FastBuildBlazeData targetData = fastBuildInfo.blazeData().get(label);
    checkState(targetData != null, "Couldn't find data for target %s", label);
    checkState(targetData.javaInfo().isPresent(), "Couldn't find Java info for target %s", label);
    JavaInfo javaInfo = targetData.javaInfo().get();
    return javaInfo.swigdeps();
  }

  // Bazel uses '/' for separators on Windows too (I haven't tested that, but see
  // WindowsOsPathPolicy#getSeparator and the comment above PathFragment)
  String getTestBinary(Label label) {
    StringBuilder sb = new StringBuilder();
    if (label.isExternal()) {
      sb.append("/external/").append(label.externalWorkspaceName()).append('/');
    }
    sb.append(label.blazePackage()).append('/').append(label.targetName());
    return sb.toString();
  }

  private static void addTestSizeVariables(
      JavaCommandBuilder commandBuilder, JavaInfo targetJavaInfo) {
    String testSize = targetJavaInfo.testSize().orElse("medium");
    int testTimeout;
    switch (testSize) {
      case "small":
        testTimeout = 60;
        break;
      case "medium":
        testTimeout = 300;
        break;
      case "large":
        testTimeout = 900;
        break;
      case "enormous":
        testTimeout = 3600;
        break;
      default:
        throw new IllegalStateException("Unknown test size '" + testSize + "'");
    }

    commandBuilder
        .addEnvironmentVariable("TEST_SIZE", testSize)
        .addEnvironmentVariable("TEST_TIMEOUT", Integer.toString(testTimeout));
  }

  /**
   * Adds environment variables and performs other setup (creating/removing directories) related to
   * the Google test runner output.
   */
  private void configureTestOutputs(
      Project project, JavaCommandBuilder commandBuilder, Label target, BlazeInfo blazeInfo)
      throws ExecutionException {

    FileOperationProvider files = FileOperationProvider.getInstance();

    File blazeTestlogs = blazeInfo.getBlazeTestlogsDirectory();
    File testOutputDir = new File(blazeTestlogs, getTestBinary(target));

    File undeclaredOutputsAnnotationsDir = new File(testOutputDir, "test.outputs_manifest");
    File undeclaredOutputsDir = new File(testOutputDir, "test.outputs");

    files.mkdirs(undeclaredOutputsAnnotationsDir);
    files.mkdirs(undeclaredOutputsDir);

    commandBuilder
        .addEnvironmentVariable(
            "TEST_INFRASTRUCTURE_FAILURE_FILE",
            getTestOutputFile(testOutputDir, "test.infrastructure_failure"))
        .addEnvironmentVariable(
            "TEST_LOGSPLITTER_OUTPUT_FILE",
            getTestOutputFile(testOutputDir, "test.raw_splitlogs/test.splitlogs"))
        .addEnvironmentVariable(
            "TEST_PREMATURE_EXIT_FILE", getTestOutputFile(testOutputDir, "test.exited_prematurely"))
        .addEnvironmentVariable(
            "TEST_UNDECLARED_OUTPUTS_ANNOTATIONS_DIR", undeclaredOutputsAnnotationsDir.toString())
        .addEnvironmentVariable("TEST_UNDECLARED_OUTPUTS_DIR", undeclaredOutputsDir.toString())
        .addEnvironmentVariable(
            "TEST_UNUSED_RUNFILES_LOG_FILE",
            getTestOutputFile(testOutputDir, "test.unused_runfiles_log"))
        .addEnvironmentVariable(
            "TEST_WARNINGS_OUTPUT_FILE", getTestOutputFile(testOutputDir, "test.warnings"));

    if (Blaze.getBuildSystemName(project).equals(BuildSystemName.Blaze)) {
      File testDiagnosticsDir = new File(testOutputDir, "test.test_diagnostics");

      try {
        if (testDiagnosticsDir.exists()) {
          // macs don't support SecureDirectoryStream, so we need to instruct guava to allow
          // insecure recursive deletion.
          // TODO(brendandouglas): don't delete files recursively on macs
          files.deleteRecursively(testDiagnosticsDir, /* allowInsecureDelete= */ SystemInfo.isMac);
        }
      } catch (IOException e) {
        throw new ExecutionException(e);
      }

      commandBuilder.addEnvironmentVariable(
          "TEST_DIAGNOSTICS_OUTPUT_DIR", testDiagnosticsDir.toString());
    }
  }

  private static void addJvmOptsFromBlazeFlags(
      BlazeCommandRunConfiguration configuration, JavaCommandBuilder commandBuilder) {
    RunConfigurationState state = configuration.getHandler().getState();
    if (!(state instanceof BlazeJavaRunConfigState)) {
      return;
    }
    List<String> blazeFlags =
        ((BlazeJavaRunConfigState) state).getBlazeFlagsState().getFlagsForExternalProcesses();
    addJvmOptsFromBlazeFlags(blazeFlags, commandBuilder);
  }

  @VisibleForTesting
  static void addJvmOptsFromBlazeFlags(List<String> blazeFlags, JavaCommandBuilder commandBuilder) {
    for (int i = 0; i < blazeFlags.size(); ++i) {
      String flag = blazeFlags.get(i);
      if (flag.equals("--jvmopt") && i + 1 < blazeFlags.size()) {
        commandBuilder.addJvmArgument(blazeFlags.get(i + 1));
        i++;
      } else if (flag.startsWith("--jvmopt=")) {
        commandBuilder.addJvmArgument(flag.substring("--jvmopt=".length()));
      }
    }
  }

  private static String getTestOutputFile(File testOutputDir, String filename) {
    return new File(testOutputDir, filename).toString();
  }
}
