/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.sdkcompat.cidr.CidrSwitchBuilderAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.toolchains.DefaultCidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

final class BlazeCompilerSettings extends OCCompilerSettings {
  private final CidrToolEnvironment toolEnvironment = new DefaultCidrToolEnvironment();

  private final Project project;
  @Nullable private final File cCompiler;
  @Nullable private final File cppCompiler;
  private final CidrCompilerSwitches cCompilerSwitches;
  private final CidrCompilerSwitches cppCompilerSwitches;

  BlazeCompilerSettings(
      Project project,
      @Nullable File cCompiler,
      @Nullable File cppCompiler,
      ImmutableList<String> cFlags,
      ImmutableList<String> cppFlags) {
    this.project = project;
    this.cCompiler = cCompiler;
    this.cppCompiler = cppCompiler;
    this.cCompilerSwitches = getCompilerSwitches(cFlags);
    this.cppCompilerSwitches = getCompilerSwitches(cppFlags);
  }

  @Override
  public OCCompilerKind getCompiler(OCLanguageKind languageKind) {
    if (languageKind == OCLanguageKind.C || languageKind == OCLanguageKind.CPP) {
      return OCCompilerKind.CLANG;
    }
    return OCCompilerKind.UNKNOWN;
  }

  @Override
  public File getCompilerExecutable(OCLanguageKind lang) {
    if (lang == OCLanguageKind.C) {
      return cCompiler;
    } else if (lang == OCLanguageKind.CPP) {
      return cppCompiler;
    }
    // We don't support objective c/c++.
    return null;
  }

  @Override
  public File getCompilerWorkingDir() {
    return WorkspaceRoot.fromProject(project).directory();
  }

  @Override
  public CidrToolEnvironment getEnvironment() {
    return toolEnvironment;
  }

  @Override
  public CidrCompilerSwitches getCompilerSwitches(
      OCLanguageKind lang, @Nullable VirtualFile sourceFile) {
    if (lang == OCLanguageKind.C) {
      return cCompilerSwitches;
    }
    if (lang == OCLanguageKind.CPP) {
      return cppCompilerSwitches;
    }
    return new CidrSwitchBuilderAdapter().build();
  }

  private static CidrCompilerSwitches getCompilerSwitches(List<String> allCompilerFlags) {
    return new CidrSwitchBuilderAdapter().addAllRaw(allCompilerFlags).build();
  }
}
