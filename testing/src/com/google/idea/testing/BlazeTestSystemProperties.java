/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.PlatformUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Test utilities specific to running IntelliJ integration tests in a blaze/bazel environment. To be
 * used with IntellijIntegrationSuite runner.
 */
class BlazeTestSystemProperties {

  private BlazeTestSystemProperties() {}

  /** Sets up the necessary system properties for running IntelliJ tests via blaze/bazel. */
  public static void configureSystemProperties() {
    final var sandbox = new File(TestUtils.getTmpDirFile(), "_intellij_test_sandbox");

    redirectIdePathsToSandbox(sandbox);
    linkSdkDirectoriesIntoSandbox();
    configurePluginCompatibility();
    relaxFileSystemChecks();

    setIfEmpty("idea.classpath.index.enabled", "false");
    setIfEmpty("idea.force.use.core.classloader", "true");
  }

  /** Points all IDE state (config, caches, logs, preferences, home) at the test sandbox. */
  private static void redirectIdePathsToSandbox(File sandbox) {
    setSandboxPath("idea.home.path", new File(sandbox, "home"));
    setSandboxPath("idea.config.path", new File(sandbox, "config"));
    setSandboxPath("idea.system.path", new File(sandbox, "system"));

    final var testUndeclaredOutputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testUndeclaredOutputsDir != null) {
      setSandboxPath("idea.log.path", new File(testUndeclaredOutputsDir, "logs"));
    }

    setSandboxPath("java.util.prefs.userRoot", new File(sandbox, "userRoot"));
    setSandboxPath("java.util.prefs.systemRoot", new File(sandbox, "systemRoot"));

    // Reset the home directory to a temporary location so tests can neither read nor
    // write the real user home (e.g. analytics settings or a local SDK under ~/Android).
    System.setProperty("user.home", new File(sandbox, "userhome").getAbsolutePath());
  }

  /**
   * Links the SDK {@code bin} and {@code lib} directories into the sandbox home.
   *
   * <p>On the radler/Rider code path the platform accesses {@code <idea.home.path>/lib} (and {@code
   * /bin}) very early during application load, before any test setUp would run. Since {@code
   * idea.home.path} points at the empty sandbox, those directories have to be linked to the real
   * SDK beforehand.
   */
  private static void linkSdkDirectoriesIntoSandbox() {
    final var sdkRoot = findSdkRoot();
    linkSandboxDir(sdkRoot, "bin", Path.of(PathManager.getBinPath()));
    linkSandboxDir(sdkRoot, "lib", Path.of(PathManager.getLibPath()));
  }

  private static Path findSdkRoot() {
    // app.jar lives at <sdk_root>/lib/app.jar; PathManager.getJarPathForClass works
    // without the application being initialized.
    final var appJar = PathManager.getJarPathForClass(Application.class);
    if (appJar == null) {
      throw new RuntimeException("Could not locate the platform jar");
    }

    final var libDir = Path.of(appJar).getParent();
    if (libDir == null || !libDir.getFileName().toString().equals("lib")) {
      throw new RuntimeException("Unexpected platform jar location: " + appJar);
    }

    return libDir.getParent();
  }

  private static void linkSandboxDir(Path sdkRoot, String dirName, Path link) {
    final var target = sdkRoot.resolve(dirName);
    if (!Files.exists(target)) {
      throw new RuntimeException("Missing SDK directory: " + target);
    }

    try {
      Files.deleteIfExists(link);
      Files.createSymbolicLink(link, target);
    } catch (IOException e) {
      throw new RuntimeException("Could not create " + dirName + " path symlink", e);
    }
  }

  /**
   * Reports the build number (and matching platform prefix) the tests should use, so plugins with a
   * since-build/until-build restriction are considered compatible.
   */
  private static void configurePluginCompatibility() {
    final var buildNumber = resolveBuildNumber();
    setIfEmpty("idea.plugins.compatible.build", buildNumber);
    setIfEmpty(PlatformUtils.PLATFORM_PREFIX_KEY, determinePlatformPrefix(buildNumber));
  }

  private static String resolveBuildNumber() {
    final var apiVersion = readApiVersionNumber();
    return apiVersion != null ? apiVersion : BuildNumber.currentVersion().asString();
  }

  @Nullable
  private static String readApiVersionNumber() {
    final var apiVersionFilePath = System.getProperty("blaze.idea.api.version.file");
    if (apiVersionFilePath == null) {
      throw new RuntimeException("No api_version_file found in runfiles directory");
    }

    final var runfilesWorkspaceRoot = System.getProperty("user.dir");
    if (runfilesWorkspaceRoot == null) {
      throw new RuntimeException("Runfiles workspace root not found");
    }

    final var apiVersionFile = Path.of(runfilesWorkspaceRoot, apiVersionFilePath);
    if (!Files.isReadable(apiVersionFile)) {
      return null;
    }

    try {
      return Files.readString(apiVersionFile, StandardCharsets.UTF_8).lines().findFirst().orElse(null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private static String determinePlatformPrefix(String buildNumber) {
    if (buildNumber.startsWith("IU")) { // IntelliJ Ultimate
      return null;
    } else if (buildNumber.startsWith("IC")) { // IntelliJ Community
      return "Idea";
    } else if (buildNumber.startsWith("CL")) { // CLion
      return "CLion";
    } else {
      throw new RuntimeException("Unable to determine platform prefix for build: " + buildNumber);
    }
  }

  /**
   * Disables the VfsRootAccess check. Tests otherwise fail when accessing files outside the project
   * roots; in particular {@code BuiltinsVirtualFileProviderBaseImpl} accesses class path jars that
   * contain {@code kotlin} packages, which would need to be added to the allow list instead.
   */
  private static void relaxFileSystemChecks() {
    System.setProperty("NO_FS_ROOTS_ACCESS_CHECK", "true");
  }

  private static void setSandboxPath(String property, File path) {
    path.mkdirs();
    setIfEmpty(property, path.getPath());
  }

  private static void setIfEmpty(String property, @Nullable String value) {
    if (value == null) {
      return;
    }
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }
}
