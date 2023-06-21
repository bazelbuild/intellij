/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream.LineProcessor;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.cpp.XcodeCompilerSettingsProvider.XcodeCompilerSettingsException.IssueKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;


public class XcodeCompilerSettingsProviderImpl implements XcodeCompilerSettingsProvider {

  private static final String QUERY_XCODE_VERSION_STARLARK_EXPR = "`{} {}`.format(providers(target)[`XcodeProperties`].xcode_version, providers(target)[`XcodeProperties`].default_macos_sdk_version) if providers(target) and `XcodeProperties` in providers(target) else ``".replace(
      '`', '"');

  // This only exists because it's impossible to escape a `deps()` query expression correctly in a Java string.
  private static final String[] QUERY_XCODE_VERSION_SCRIPT_LINES = new String[]{
      "#!/bin/bash",
      "__BAZEL_BIN__ cquery \\",
      " 'deps(\"@bazel_tools//tools/osx:current_xcode_config\")' \\",
      " --output=starlark \\",
      " --starlark:expr='" + QUERY_XCODE_VERSION_STARLARK_EXPR + "'",
  };

  @Override
  public Optional<XcodeCompilerSettings> fromContext(BlazeContext context, Project project)
      throws XcodeCompilerSettingsException {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    BuildSystem.BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);

    Optional<XcodeAndSdkVersions> xcodeAndSdkVersions = XcodeCompilerSettingsProviderImpl.queryXcodeAndSdkVersions(
        context, invoker,
        workspaceRoot);

    if (!xcodeAndSdkVersions.isPresent()) {
      return Optional.empty();
    }
    XcodeAndSdkVersions versions = xcodeAndSdkVersions.get();

    String macosSdkVersion = versions.sdkVersion;
    Optional<XcodeCompilerSettings> settings = Optional.empty();
    switch (versions.xcodeKind) {
      case XCODE_APP: {
        if (!versions.xcodeVersion.isPresent()) {
          throw new XcodeCompilerSettingsException(IssueKind.QUERY_DEVELOPER_DIR,
              "Bazel thinks you're using a full Xcode installation, but was unable to parse the version for it. Please make sure your Xcode installation is correct, or switch to command line tools.");
        }
        String xcodeVersion = versions.xcodeVersion.get();
        String developerDir = XcodeCompilerSettingsProviderImpl.queryDeveloperDir(context, invoker,
            workspaceRoot, xcodeVersion);
        String sdkVersionString = String.format("MacOSX%s.sdk", macosSdkVersion);
        settings = Optional.of(XcodeCompilerSettings.create(
            Path.of(developerDir),
            Path.of(developerDir, "Platforms", "MacOSX.platform", "Developer", "SDKs",
                sdkVersionString)
        ));
        break;
      }
      case COMMAND_LINE_TOOLS: {
        String developerDirRaw = "/Library/Developer/CommandLineTools";
        Path developerDir = Path.of(developerDirRaw);
        // Some users might not have command line tools installed.
        // This should never trigger as Bazel requires that at least one of Xcode or CLTs are in the system to work.
        // However, *if* it triggers (e.g. because Bazel changes this requirement),
        // we want to handle the case.
        if (!developerDir.toFile().exists()) {
          return Optional.empty();
        }
        settings = Optional.of(XcodeCompilerSettings.create(
            developerDir,
            Path.of(developerDirRaw, "SDKs", "MacOSX.sdk")
        ));
        if (!XcodeCompilerSettingsProviderImpl.commandLineToolsHaveValidClang(settings.get())) {
          throw new XcodeCompilerSettingsException(IssueKind.QUERY_DEVELOPER_DIR,
              "Your CommandLineTools installation does not have a usable clang. This is often caused by a corrupted macOS upgrade. Please reinstall CommandLineTools or install Xcode");
        }
        break;
      }
    }

    if (!settings.isPresent()) {
      throw new XcodeCompilerSettingsException(IssueKind.QUERY_DEVELOPER_DIR,
          "Unable to classify your CC toolchain as either an Xcode app or a CommandLineTools installation. Please check that the installation is correct");
    }

    return settings;
  }

  // Sometimes, a macOS upgrade can corrupt the installation of CommandLineTools.
  // We detect that we can at least run `clang --version`.
  private static boolean commandLineToolsHaveValidClang(XcodeCompilerSettings xcodeCompilerSettings) {
    Path clang = Path.of(xcodeCompilerSettings.getDeveloperDir().toString(), "usr", "bin", "clang");
    if (!clang.toFile().exists()) {
      return false;
    }

    // We try to run clang --version to know that it's a working binary.
    int result = ExternalTask.builder().args(clang.toString(), "--version").build().run();
    return result == 0;
  }

  static class CaptureLineProcessor implements LineProcessor {
    StringBuilder stream;

    public CaptureLineProcessor() {
      this.stream = new StringBuilder();
    }

    @Override
    public String toString() {
      return this.stream.toString();
    }

    @Override
    public boolean processLine(String line) {
      this.stream.append(line);
      return true;
    }
  }

  /**
   * Pass the version to the xcode locator, so that it returns the developer dir. This is a mirror
   * of Bazel's own behavior: Ref:
   * https://github.com/bazelbuild/bazel/blob/1811e82ca4e68c2dd52eed7907c3d1926237e18a/src/main/java/com/google/devtools/build/lib/exec/local/XcodeLocalEnvProvider.java#L241
   */
  private static String queryDeveloperDir(BlazeContext context, BuildInvoker invoker,
      WorkspaceRoot workspaceRoot, String xcodeVersion) throws XcodeCompilerSettingsException {
    BlazeCommand.Builder runXcodeLocator = BlazeCommand.builder(invoker, BlazeCommandName.RUN);
    runXcodeLocator.addTargets(
        Label.fromStringSafe("@bazel_tools//tools/osx:xcode-locator"));
    runXcodeLocator.addExeFlags(xcodeVersion).build();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CaptureLineProcessor errLines = new CaptureLineProcessor();
    int result = ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(runXcodeLocator.build())
        .context(context)
        .stderr(
            LineProcessingOutputStream.of(
                ImmutableList.<LineProcessor>builder().add(errLines).addAll(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)
                ).build()
            )
        )
        .stdout(outputStream)
        .build()
        .run();

    if (result != 0) {
      throw new XcodeCompilerSettingsException(
          IssueKind.QUERY_DEVELOPER_DIR,
          String.format("stderr: \"%s\"\nstdout: \"%s\"", errLines, outputStream));
    }
    return outputStream.toString().strip();
  }

  /**
   * Use bazel query to find out the Xcode and macOS SDK versions. As per the architecture of the
   * codebase, this should be fetched in the aspect of the plugin. However,
   * https://github.com/bazelbuild/bazel/issues/17871 prevents us from actually propagating the
   * information form the aspect. So, we resort to a separate cquery.
   * <p>
   * The cquery is implemented as a script file because it's impossible to escape a `deps()`
   * expression properly in Java.
   */
  private static Optional<XcodeAndSdkVersions> queryXcodeAndSdkVersions(BlazeContext context,
      BuildInvoker invoker, WorkspaceRoot workspaceRoot)
      throws XcodeCompilerSettingsException {
    File blazeCqueryWrapper = null;
    try {
      blazeCqueryWrapper =
          FileUtil.createTempFile("xcode_cquery", ".sh", true /* deleteOnExit */);
      if (!blazeCqueryWrapper.setExecutable(true)) {
        throw new XcodeCompilerSettingsException(IssueKind.FETCH_XCODE_VERSION,
            "Error getting Xcode info: Couldn't make cquery script executable");
      }
      try (PrintWriter pw = new PrintWriter(blazeCqueryWrapper, UTF_8.name())) {
        Arrays.stream(QUERY_XCODE_VERSION_SCRIPT_LINES).forEach(line -> {
          pw.println(line.replace("__BAZEL_BIN__", invoker.getBinaryPath()));
        });
      }
    } catch (IOException e) {
      throw new XcodeCompilerSettingsException(IssueKind.FETCH_XCODE_VERSION,
          "Error getting Xcode info: Couldn't create cquery script", e);
    }

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CaptureLineProcessor errLines = new CaptureLineProcessor();
    int result = ExternalTask.builder(workspaceRoot)
        .arg(blazeCqueryWrapper.getAbsolutePath())
        .context(context)
        .stderr(
            LineProcessingOutputStream.of(
                ImmutableList.<LineProcessor>builder().add(errLines).addAll(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)
                ).build()
            ))
        .stdout(outputStream)
        .build()
        .run();

    if (result == 37) {
      // If the target `@bazel_tools//tools/osx:xcode-locator` doesn't exist (e.g. running on Linux),
      // Bazel will crash with error code 37.
      // This is expected, and it means there is no xcode info to be fetched.
      return Optional.empty();
    }
    if (result != 0) {
      throw new XcodeCompilerSettingsException(
          IssueKind.FETCH_XCODE_VERSION,
          String.format("stderr: \"%s\"\nstdout: \"%s\"", errLines, outputStream));
    }

    // This will not be more than 10 lines. It's fine to store it in a string.
    String output = outputStream.toString(StandardCharsets.UTF_8);
    for (String line : output.split("\n")) {
      if (!line.isBlank()) {
        String[] versions = line.split(" ");
        if (versions.length != 2) {
          throw new XcodeCompilerSettingsException(IssueKind.FETCH_XCODE_VERSION,
              String.format("Error parsing Xcode versions from cquery output: %s", output));
        }
        // If you only have CommandLineTools installed),
        // the query will fail to fetch an xcode version.
        // But it will still return an SDK version.
        if (versions[0].equals("None")) {
          return Optional.of(XcodeAndSdkVersions.forCommandLineTools(versions[1]));
        }
        // Returning the first occurrence here is fine
        // because the target we query is an alias for the current config anyway.
        return Optional.of(XcodeAndSdkVersions.forXcodeApplication(versions[0], versions[1]));
      }
    }
    throw new XcodeCompilerSettingsException(IssueKind.FETCH_XCODE_VERSION,
        String.format("Could not get a usable Xcode version from cquery output: %s", output));
  }

  private enum XcodeInstallationKind {
    XCODE_APP,
    COMMAND_LINE_TOOLS,
  }

  // TODO: Replace with Java records when we can target a newer version.
  private static class XcodeAndSdkVersions {

    XcodeInstallationKind xcodeKind;
    Optional<String> xcodeVersion;
    String sdkVersion;

    private XcodeAndSdkVersions(XcodeInstallationKind xcodeKind, Optional<String> xcodeVersion, String sdkVersion) {
      this.xcodeKind = xcodeKind;
      this.xcodeVersion = xcodeVersion;
      this.sdkVersion = sdkVersion;
    }

    public static XcodeAndSdkVersions forXcodeApplication(String xcodeVersion, String sdkVersion) {
      return new XcodeAndSdkVersions(XcodeInstallationKind.XCODE_APP, Optional.of(xcodeVersion), sdkVersion);
    }

    public static XcodeAndSdkVersions forCommandLineTools(String sdkVersion) {
      return new XcodeAndSdkVersions(XcodeInstallationKind.COMMAND_LINE_TOOLS, Optional.empty(), sdkVersion);
    }
  }
}
