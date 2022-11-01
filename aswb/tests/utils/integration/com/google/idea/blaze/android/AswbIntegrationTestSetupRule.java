/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.ExternalResource;

/**
 * Sets up the test environment specific to running ASwB integration tests in a blaze/bazel
 * environment. This setup prepares the test environment for ASwB integration tests in the same way
 * {@link com.android.tools.idea.IdeaTestSuite} does for regular android studio integration tests
 * but this rule does not pull in all the dependencies IdeaTestSuite does and should be modified
 * incrementally if the need for those dependencies arise. Should be instantiated as a @ClassRule in
 * the outermost test class/suite.
 */
public class AswbIntegrationTestSetupRule extends ExternalResource {
  /** {@link com.google.idea.testing.BlazeTestSystemPropertiesRule#configureSystemProperties()} */
  public static final String SANDBOX_IDEA_HOME = "_intellij_test_sandbox/home/";

  @Override
  protected void before() throws Throwable {
    symlinkRequiredLibraries();
  }

  private void symlinkRequiredLibraries() {
    /*
     * Android annotation requires a different path to match one of the candidate paths in
     * {@link com.android.tools.idea.startup.ExternalAnnotationsSupport.DEVELOPMENT_ANNOTATIONS_PATHS}
     */
    symlinkToSandboxHome(
        "tools/adt/idea/android/annotations", SANDBOX_IDEA_HOME + "android/android/annotations");
    symlinkToSandboxHome("prebuilts/studio/layoutlib");
    symlinkToSandboxHome("prebuilts/studio/sdk");
  }

  private static void symlinkToSandboxHome(String target) {
    symlinkToSandboxHome(target, target);
  }

  private static void symlinkToSandboxHome(String target, String customLink) {
    try {
      File file = new File(getWorkspaceRoot(), target);
      if (!file.exists()) {
        throw new IllegalStateException("Cannot symlink to idea home: " + target);
      }
      Path targetPath = file.toPath();
      Path linkName = Paths.get(System.getProperty("java.io.tmpdir"), customLink);
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static synchronized File getWorkspaceRoot() {
    File workspaceRoot = null;

    // Use the sandboxed root provided for bazel tests.
    String workspace = System.getenv("TEST_WORKSPACE");
    String workspaceParent = System.getenv("TEST_SRCDIR");
    File curDir = new File("");
    if (workspace != null && workspaceParent != null) {
      curDir = new File(workspaceParent, workspace);
      workspaceRoot = curDir;
    }
    File initialDir = curDir;

    // Look to see if there's a larger outermost workspace since we might be within a nested
    // workspace.
    while (curDir != null) {
      curDir = curDir.getAbsoluteFile();
      if (new File(curDir, "WORKSPACE").exists()) {
        workspaceRoot = curDir;
      }
      curDir = curDir.getParentFile();
    }

    if (workspaceRoot == null) {
      throw new IllegalStateException(
          "Could not find WORKSPACE root. Is the original working directory a "
              + "subdirectory of the Android Studio codebase?\n\n"
              + "pwd = "
              + initialDir.getAbsolutePath());
    }

    return workspaceRoot;
  }
}
