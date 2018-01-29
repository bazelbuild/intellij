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
package com.google.idea.sdkcompat.clion;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration;
import com.jetbrains.cidr.execution.Installer;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;

/** Adapter to bridge different SDK versions. */
public abstract class BlazeGDBDriverConfigurationBase extends GDBDriverConfiguration {
  public BlazeGDBDriverConfigurationBase(Project project) {}

  public abstract void modifyCommandLine(GeneralCommandLine commandLine);

  @Override
  public GeneralCommandLine createDriverCommandLine(DebuggerDriver driver, Installer installer)
      throws ExecutionException {
    GeneralCommandLine commandLine = super.createDriverCommandLine(driver, installer);
    modifyCommandLine(commandLine);
    return commandLine;
  }
}
