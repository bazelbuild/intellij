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
package com.google.idea.blaze.android.sync.model.idea;

import com.android.tools.idea.model.ClassJarProvider;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Returns no class jars. Used to disable the layout editor
 * loading jars.
 */
public class NullClassJarProvider extends ClassJarProvider {
  @Nullable
  @Override
  public VirtualFile findModuleClassFile(@NotNull String className, @NotNull Module module) {
    return null;
  }

  @NotNull
  @Override
  public List<VirtualFile> getModuleExternalLibraries(@NotNull Module module) {
    return ImmutableList.of();
  }
}
