/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;

/** Adapter to bridge different SDK versions. */
public class CompilerInfoCacheAdapter extends CompilerInfoCache {
  public Result getCompilerInfoCache(
      Project project,
      OCCompilerSettingsAdapter compilerSettings,
      OCLanguageKind kind,
      VirtualFile sourceFile) {
    CidrCompilerSwitches switches = compilerSettings.getCompilerSwitches(kind, sourceFile);
    OCCompilerKind compilerKind = compilerSettings.getCompiler(kind);
    File compilerExecutable = compilerSettings.getCompilerExecutable(kind);
    File workingDirectory = compilerSettings.getCompilerWorkingDir();
    CidrToolEnvironment environment = compilerSettings.getEnvironment();
    return getCompilerInfoCache(
        kind, switches, compilerKind, compilerExecutable, workingDirectory, environment);
  }
}
