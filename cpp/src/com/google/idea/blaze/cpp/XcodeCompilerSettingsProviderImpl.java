package com.google.idea.blaze.cpp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
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

  private static final String QUERY_XCODE_VERSION_STARLARK_EXPR = "`{} {}`.format(providers(target)[`XcodeProperties`].xcode_version, providers(target)[`XcodeProperties`].default_macos_sdk_version) if `XcodeProperties` in providers(target) else ``".replace(
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

    Optional<XcodeAndSdkVersions> xcodeAndSdkVersions = XcodeCompilerSettingsProviderImpl.queryXcodeAndSdkVersions(context, invoker,
        workspaceRoot);

    if (xcodeAndSdkVersions.isPresent()) {
      XcodeAndSdkVersions versions = xcodeAndSdkVersions.get();
      String xcodeVersion = versions.xcodeVersion;
      String macosSdkVersion = versions.sdkVersion;
      String developerDir = XcodeCompilerSettingsProviderImpl.queryDeveloperDir(context, invoker, workspaceRoot, xcodeVersion);
      String sdkVersionString = String.format("MacOSX%s.sdk", macosSdkVersion);
      return Optional.of(new XcodeCompilerSettings(
          Path.of(developerDir),
          Path.of(developerDir, "Platforms", "MacOSX.platform", "Developer", "SDKs",
              sdkVersionString)
      ));
    }
    return Optional.empty();
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
    ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    int result = ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(runXcodeLocator.build())
        .context(context)
        .stderr(errStream)
        .stdout(outputStream)
        .build()
        .run();

    if (result != 0) {
      throw new XcodeCompilerSettingsException(
          IssueKind.QUERY_DEVELOPER_DIR,
          String.format("stderr: \"%s\"\nstdout: \"%s\"", errStream, outputStream));
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
    ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    int result = ExternalTask.builder(workspaceRoot)
        .arg(blazeCqueryWrapper.getAbsolutePath())
        .context(context)
        .stderr(errStream)
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
          String.format("stderr: \"%s\"\nstdout: \"%s\"", errStream, outputStream));
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
        // Returning the first occurrence here is fine
        // because the target we query is an alias for the current config anyway.
        return Optional.of(new XcodeAndSdkVersions(versions[0], versions[1]));
      }
    }
    throw new XcodeCompilerSettingsException(IssueKind.FETCH_XCODE_VERSION,
        String.format("Could not get a usable Xcode version from cquery output: %s", output));
  }


  // TODO: Replace with Java records when we can target a newer version.
  private static class XcodeAndSdkVersions {

    String xcodeVersion;
    String sdkVersion;

    public XcodeAndSdkVersions(String xcodeVersion, String sdkVersion) {
      this.xcodeVersion = xcodeVersion;
      this.sdkVersion = sdkVersion;
    }
  }
}
