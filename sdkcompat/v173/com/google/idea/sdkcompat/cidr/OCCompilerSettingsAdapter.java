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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.toolchains.CompilerInfoCache.Entry;
import com.jetbrains.cidr.toolchains.OCCompilerSettingsBackedByCompilerCache;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerSettingsAdapter extends OCCompilerSettingsBackedByCompilerCache {
  public abstract Project getProject();

  public abstract CompilerInfoCacheAdapter getCompilerInfo();

  @Override
  public CidrCompilerResult<Entry> getCompilerInfo(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile) {
    return getCompilerInfo().getCompilerInfoCache(getProject(), this, ocLanguageKind, virtualFile);
  }

  public abstract String getCompilerKeyString(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile);

  @Override
  public String getCompilerKey(OCLanguageKind kind, VirtualFile sourcefile) {
    return getCompilerKeyString(kind, sourcefile);
  }

  @Override
  public CidrToolEnvironment getEnvironment() {
    return new CPPEnvironmentAdapter();
  }
}
