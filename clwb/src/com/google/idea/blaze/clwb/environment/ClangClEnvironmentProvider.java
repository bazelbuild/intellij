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

import com.google.idea.blaze.cpp.BlazeCompilerSettings;
import com.google.idea.blaze.cpp.CppEnvironmentProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet.Kind;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind;
import com.jetbrains.cidr.toolchains.OSType;
import javax.annotation.Nullable;

class ClangClEnvironmentProvider implements CppEnvironmentProvider {

  private static final Logger LOG = Logger.getInstance(ClangClEnvironmentProvider.class);

  private static final String TOOLCHAIN_NAME = "synthetic_clang_cl_bazel";

  @Override
  public @Nullable CidrToolEnvironment create(BlazeCompilerSettings settings) {
    if (settings.getCompiler(CLanguageKind.C) != ClangClCompilerKind.INSTANCE) {
      return null;
    }

    final var toolSetPath = MSVCEnvironmentUtil.getToolSetPath(null);
    if (toolSetPath == null) {
      LOG.warn("Could not find tool set path.");
      return null;
    }

    final var toolchain = new CPPToolchains.Toolchain(OSType.getCurrent());
    toolchain.setName(TOOLCHAIN_NAME);
    toolchain.setToolSetKind(Kind.MSVC);
    toolchain.setToolSetPath(toolSetPath);

    return new CPPEnvironment(toolchain);
  }
}
