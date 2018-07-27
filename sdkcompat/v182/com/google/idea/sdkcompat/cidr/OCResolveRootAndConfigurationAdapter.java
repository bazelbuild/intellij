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
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adapter to bridge different SDK versions. */
public class OCResolveRootAndConfigurationAdapter extends OCResolveRootAndConfiguration {

  public OCResolveRootAndConfigurationAdapter(
      @Nullable OCResolveConfiguration configuration,
      @NotNull OCLanguageKind kind,
      @Nullable VirtualFile rootFile) {
    super(configuration, kind, rootFile);
  }
}
