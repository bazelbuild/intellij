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

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.OCIncludeMap;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures.Type;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.OCCompilerSettingsBackedByCompilerCache;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCResolveConfigurationAdapter extends UserDataHolderBase
    implements OCResolveConfiguration {

  /* v171 */
  public abstract OCCompilerMacros getCompilerMacros();

  @Override
  public Map<Type<?>, ?> getCompilerFeatures(
      OCLanguageKind kind, @Nullable VirtualFile virtualFile) {
    OCCompilerSettings compilerSettings = getCompilerSettings();
    if (!(compilerSettings instanceof OCCompilerSettingsBackedByCompilerCache)) {
      return Collections.emptyMap();
    }

    OCCompilerSettingsBackedByCompilerCache backedCompilerSettings =
        (OCCompilerSettingsBackedByCompilerCache) compilerSettings;
    return backedCompilerSettings.getCompilerFeatures(kind, virtualFile);
  }

  @Override
  public OCIncludeMap getIncludeMap() {
    return OCIncludeMap.EMPTY;
  }
}
