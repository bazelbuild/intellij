/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.ArchitectureType;
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionGDBDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import java.io.File;

/** GDB configuration that allows overriding the working directory. */
public class BlazeCLionGDBDriverConfiguration extends CLionGDBDriverConfiguration {
  private final File workspaceRootDirectory;

  public BlazeCLionGDBDriverConfiguration(Project project) {
    super(project, ToolchainUtils.getToolchain());
    workspaceRootDirectory = WorkspaceRoot.fromProject(project).directory();
  }

  @Override
  public GeneralCommandLine createDriverCommandLine(
      DebuggerDriver driver, ArchitectureType architectureType) throws ExecutionException {
    GeneralCommandLine cl = super.createDriverCommandLine(driver, architectureType);
    cl.setWorkDirectory(workspaceRootDirectory);
    return cl;
  }
}
