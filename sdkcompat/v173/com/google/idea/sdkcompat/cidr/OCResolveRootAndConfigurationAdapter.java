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

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public class OCResolveRootAndConfigurationAdapter extends OCResolveRootAndConfiguration {

  public static OCResolveRootAndConfigurationAdapter safelyConstruct(
      @Nullable OCResolveConfiguration configuration,
      @Nullable OCLanguageKind kind,
      @Nullable VirtualFile rootFile) {
    if (kind == null && configuration != null && rootFile != null) {
      kind = configuration.getDeclaredLanguageKind(rootFile);
    }
    if (kind == null) {
      // Reasonable guess
      kind = OCLanguageKind.CPP;
    }
    return new OCResolveRootAndConfigurationAdapter(configuration, kind, rootFile);
  }

  private OCResolveRootAndConfigurationAdapter(
      @Nullable OCResolveConfiguration configuration,
      OCLanguageKind kind,
      @Nullable VirtualFile rootFile) {
    super(configuration, kind);
  }
}
