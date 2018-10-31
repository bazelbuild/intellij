/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSettingsKey;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.io.File;
import javax.annotation.Nullable;

/** Stub {@link OCCompilerSettings} for testing. */
class StubOCCompilerSettings implements OCCompilerSettings {

  private final Project project;

  StubOCCompilerSettings(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public OCCompilerKind getCompiler(OCLanguageKind languageKind) {
    return OCCompilerKind.CLANG;
  }

  @Nullable
  @Override
  public File getCompilerExecutable(OCLanguageKind languageKind) {
    return null;
  }

  @Override
  public File getCompilerWorkingDir() {
    return VfsUtilCore.virtualToIoFile(project.getBaseDir());
  }

  @Override
  public CidrCompilerSwitches getCompilerSwitches(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile) {
    return new CidrSwitchBuilder().build();
  }

  @Nullable
  @Override
  public CompilerSettingsKey getCompilerKey(OCLanguageKind kind, @Nullable VirtualFile sourcefile) {
    return new CompilerSettingsKey(getCompilerKeyString(kind));
  }

  private String getCompilerKeyString(OCLanguageKind ocLanguageKind) {
    return getCompiler(ocLanguageKind) + "-" + ocLanguageKind.toString();
  }
}
