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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.modulemap.ModuleMapModules;
import java.io.File;
import org.jetbrains.annotations.NotNull;

final class BlazeResolveConfiguration extends BlazeResolveConfigurationTemporaryBase {

  public BlazeResolveConfiguration(
      Project project,
      WorkspacePathResolver workspacePathResolver,
      Label label,
      ImmutableCollection<ExecutionRootPath> cSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> quoteIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppIncludeDirs,
      ImmutableCollection<String> defines,
      ImmutableMap<String, String> features,
      File cCompilerExecutable,
      File cppCompilerExecutable,
      ImmutableList<String> cCompilerFlags,
      ImmutableList<String> cppCompilerFlags) {
    super(
        project,
        workspacePathResolver,
        label,
        cSystemIncludeDirs,
        cppSystemIncludeDirs,
        quoteIncludeDirs,
        cIncludeDirs,
        cppIncludeDirs,
        defines,
        features,
        cCompilerExecutable,
        cppCompilerExecutable,
        cCompilerFlags,
        cppCompilerFlags);
  }

  @NotNull
  @Override
  public ModuleMapModules getModules() {
    return ModuleMapModules.Companion.getEMPTY();
  }
}
