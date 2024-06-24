/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.clwb;

import com.google.idea.blaze.cpp.BlazeCompilerSettings;
import com.google.idea.blaze.cpp.CompilerVersionUtil;
import com.google.idea.blaze.cpp.CppEnvironmentProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.ObjectUtils;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet.Kind;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.toolchains.OSType;
import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

class MSVCEnvironmentProvider implements CppEnvironmentProvider {
  private static final Logger LOG = Logger.getInstance(MSVCEnvironmentProvider.class);

  /**
   * The environment variable used by bazel to find the Visual Studio directory.
   */
  private static final String BAZEL_VC_VAR = "BAZEL_VC";

  /**
   * Key for {@link TestModeFlags} to mock the value of BAZEL_VC.
   */
  static final Key<String> BAZEL_VC_KEY = new Key<>("BAZEL_VC_ENV");

  /**
   * Pattern to find the Visual Studio directory from the absolute path of the compiler executable.
   */
  private static final Pattern VC_DIRECTORY_PATTERN = Pattern.compile(
      "(?<path>.*)/vc/tools/msvc/.*/bin/.*/cl\\.exe".replace("/", "\\\\")
  );

  @Override
  public @Nullable CidrToolEnvironment create(BlazeCompilerSettings settings) {
    if (!CompilerVersionUtil.isMSVC(settings.getCompilerVersion())) {
      return null;
    }

    final var compiler = selectCompiler(settings);
    if (compiler == null) {
      LOG.warn("No suitable compiler found.");
      return null;
    }

    final var toolSetPath = getToolSetPath(compiler);
    if (toolSetPath == null) {
      LOG.warn("Could not find tool set path.");
      return null;
    }

    final var version = MSVCCompilerVersionCompat.getCompilerVersion(compiler);
    if (version == null) {
      LOG.warn("Could not derive arch and version.");
      return null;
    }

    final var toolchain = new CPPToolchains.Toolchain(OSType.getCurrent());
    toolchain.setName("Synthetic Bazel Toolchain");
    toolchain.setToolSetKind(Kind.MSVC);
    toolchain.setToolSetPath(toolSetPath);

    final var environment = new CPPEnvironment(toolchain);
    MSVCCompilerVersionCompat.setEnvironmentVersion(environment, version);

    return environment;
  }

  /**
   * Since there could be two different compilers for C and Cpp we need to select one. Same problem
   * as here: [BlazeConfigurationToolchainResolver.mergeCompilerVersions]
   *
   * Assumption: MSVC uses the same compiler for both C and Cpp
   */
  private static @Nullable File selectCompiler(BlazeCompilerSettings settings) {
    final var cCompiler = settings.getCompilerExecutable(CLanguageKind.C);
    final var cppCompiler = settings.getCompilerExecutable(CLanguageKind.CPP);

    if (cCompiler == null) {
      return cppCompiler;
    }
    if (cppCompiler == null) {
      return cCompiler;
    }

    if (FileUtil.compareFiles(cCompiler, cppCompiler) != 0) {
      LOG.warn("C and Cpp compiler mismatch. Defaulting to Cpp compiler.");
    }

    return cppCompiler;
  }

  private static String getBazelVC() {
    return ObjectUtils.coalesce(TestModeFlags.get(BAZEL_VC_KEY), System.getenv(BAZEL_VC_VAR));
  }

  /**
   * The MSVC toolset requires a home path. Which is the path to VisualStudio installation e.g.:
   * C:\Program Files\Microsoft Visual Studio\2022\Community
   *
   * This path can either be derived from the BAZEL_VC environment variable which points to:
   * C:\Program Files\Microsoft Visual Studio\2022\Community\VC
   *
   * Or from the compiler path. Not sure what is more reliable, so let's do both.
   */
  @VisibleForTesting
  static @Nullable String getToolSetPath(File compiler) {
    final var bazelVC = getBazelVC();
    if (bazelVC != null) {
      return new File(bazelVC).getParent();
    }

    final var path = compiler.getAbsolutePath().toLowerCase(Locale.ROOT);

    final var matcher = VC_DIRECTORY_PATTERN.matcher(path);
    if (!matcher.matches()) {
      return null;
    }

    return matcher.group("path");
  }
}
