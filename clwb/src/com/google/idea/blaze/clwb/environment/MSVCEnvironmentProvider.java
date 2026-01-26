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

package com.google.idea.blaze.clwb.environment;

import com.google.idea.blaze.cpp.BlazeCompilerSettings;
import com.google.idea.blaze.cpp.CppEnvironmentProvider;
import com.google.idea.sdkcompat.clion.OSTypeCompat;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet.Kind;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.MSVC;
import com.jetbrains.cidr.cpp.toolchains.msvc.MSVCCompilerToVersionCacheService;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import java.io.File;
import javax.annotation.Nullable;

class MSVCEnvironmentProvider implements CppEnvironmentProvider {

  private static final Logger LOG = Logger.getInstance(MSVCEnvironmentProvider.class);

  private static final String TOOLCHAIN_NAME = "synthetic_msvc_bazel";

  @Override
  public @Nullable CidrToolEnvironment create(BlazeCompilerSettings settings) {
    if (settings.getCompilerKind() != MSVCCompilerKind.INSTANCE) {
      return null;
    }

    final var compiler = selectCompiler(settings);
    if (compiler == null) {
      LOG.warn("No suitable compiler found.");
      return null;
    }

    final var toolSetPath = MSVCEnvironmentUtil.getToolSetPath(compiler.toPath());
    if (toolSetPath == null) {
      LOG.warn("Could not find tool set path.");
      return null;
    }

    final var version = ApplicationManager.getApplication()
        .getService(MSVCCompilerToVersionCacheService.class)
        .getCompilerVersion(compiler.getAbsolutePath());

    if (version == null) {
      LOG.warn("Could not derive arch and version.");
      return null;
    }

    final var toolchain = new CPPToolchains.Toolchain(OSTypeCompat.getCurrent());
    toolchain.setName(TOOLCHAIN_NAME);
    toolchain.setToolSetKind(Kind.MSVC);
    toolchain.setToolSetPath(toolSetPath);

    final var environment = new CPPEnvironment(toolchain);
    ((MSVC) environment.getToolSet()).setToolsVersion(version);

    return environment;
  }

  /**
   * Since there could be two different compilers for C and Cpp we need to select one. Same problem as here:
   * [BlazeConfigurationToolchainResolver.mergeCompilerVersions]
   * <p>
   * Assumption: MSVC uses the same compiler for both C and Cpp
   */
  private static File selectCompiler(BlazeCompilerSettings settings) {
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
}
