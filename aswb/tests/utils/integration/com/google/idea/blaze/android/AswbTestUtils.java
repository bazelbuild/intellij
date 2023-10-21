/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class AswbTestUtils {
  /** {@link com.google.idea.testing.BlazeTestSystemPropertiesRule#configureSystemProperties()} */
  public static final String SANDBOX_IDEA_HOME = "_intellij_test_sandbox/home/";

  private AswbTestUtils() {}

  public static void symlinkToSandboxHome(String target, String customLink) {
    try {
      File file = recursivelySearchForTargetFolder(getRunfilesWorkspaceRoot(), target);
      if (file == null) {
        throw new IllegalStateException("Cannot symlink to idea home: " + target);
      }
      Path targetPath = file.toPath();
      Path linkName = Paths.get(System.getProperty("java.io.tmpdir"), customLink);
      if (Files.exists(linkName) && Objects.equals(Files.readSymbolicLink(linkName), targetPath)) {
        return;
      }
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Target folder is created within the plugin folder (i.e.
   * $workspace/aswb/) and there is no environment variable that
   * provides this path. This method is used to recursively search for the target sub folder within
   * the workspace folder (which contains the plugin folder)
   */
  static File recursivelySearchForTargetFolder(File folder, String target) {
    for (File file : folder.listFiles()) {
      if (file.isDirectory()) {
        if (file.toPath().toString().contains(target)) {
          return file;
        }
        File subFolder = recursivelySearchForTargetFolder(file, target);
        if (subFolder != null) {
          return subFolder;
        }
      }
    }
    return null;
  }

  static synchronized File getRunfilesWorkspaceRoot() {
    // Use the sandboxed root provided for bazel tests.
    String workspace = requireNonNull(System.getenv("TEST_WORKSPACE"));
    String workspaceParent = requireNonNull(System.getenv("TEST_SRCDIR"));
    return new File(workspaceParent, workspace);
  }
}
