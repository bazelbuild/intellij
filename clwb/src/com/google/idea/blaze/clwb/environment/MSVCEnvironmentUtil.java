/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.environment;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.ObjectUtils;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class MSVCEnvironmentUtil {

  private static final Logger LOG = Logger.getInstance(MSVCEnvironmentUtil.class);

  /**
   * The environment variable used by bazel to find the Visual Studio directory.
   */
  public static final String BAZEL_VC_VAR = "BAZEL_VC";

  /**
   * Key for {@link TestModeFlags} to mock the value of BAZEL_VC.
   */
  @VisibleForTesting
  public static final Key<String> BAZEL_VC_KEY = new Key<>("BAZEL_VC_ENV");

  /**
   * Pattern to find the Visual Studio directory from the absolute path of the compiler executable.
   */
  private static final Pattern VC_DIRECTORY_PATTERN = Pattern.compile(
      "(?<path>.*)/vc/tools/msvc/.*/bin/.*/cl\\.exe".replace("/", "\\\\")
  );

  private static String getBazelVC() {
    return ObjectUtils.coalesce(TestModeFlags.get(BAZEL_VC_KEY), System.getenv(BAZEL_VC_VAR));
  }

  /**
   * The MSVC toolset requires a home path. Which is the path to VisualStudio installation e.g.: C:\Program
   * Files\Microsoft Visual Studio\2022\Community
   * <p>
   * This path can be derived from the BAZEL_VC environment variable which points to: C:\Program Files\Microsoft Visual
   * Studio\2022\Community\VC
   * <p>
   * Or from the compiler path. Not sure what is more reliable, so let's do both.
   */
  public static @Nullable String getToolSetPath(@Nullable Path compiler) {
    final var bazelVC = getBazelVC();

    // try to get the path from the BAZEL_VC environment variable
    if (bazelVC != null) {
      final var path = getToolSetPathFromBazelVC(bazelVC);

      if (path != null) {
        return path;
      }
    }

    // if BAZEL_VC is not set or invalid, try to derive the path from the compiler path
    if (compiler == null) {
      return null;
    }

    final var path = compiler.toAbsolutePath().toString().toLowerCase(Locale.ROOT);

    final var matcher = VC_DIRECTORY_PATTERN.matcher(path);
    if (!matcher.matches()) {
      return null;
    }

    return matcher.group("path");
  }

  private static @Nullable String getToolSetPathFromBazelVC(String bazelVC) {
    try {
      final var parent = Path.of(bazelVC).getParent();
      if (parent == null) {
        return null;
      }

      return parent.toString();
    } catch (InvalidPathException e) {
      LOG.warn("Invalid path in BAZEL_VC environment variable.", e);
      return null;
    }
  }
}
