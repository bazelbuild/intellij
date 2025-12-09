/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.clwb.ToolchainUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.ArchitectureType;
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionLLDBDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlazeLLDBDriverConfiguration extends CLionLLDBDriverConfiguration {

    private final Path workingDirectory;
    private final Path projectRoot;

    public BlazeLLDBDriverConfiguration(@NotNull Project project, Path workingDirectory,
            Path projectRoot) {
        super(project, ToolchainUtils.getToolchain());
        this.workingDirectory = workingDirectory;
        this.projectRoot = projectRoot;
    }

    @Override
    public String convertToProjectModelPath(@Nullable String absolutePath) {
        if (absolutePath == null) {
            return null;
        }

        // we need to relativize the path to the project root for breakpoints.
        // LLDB started from execroot can set only relative breakpoints, and for
        // absolute it requires path mapping to be set, which is quite hard to
        // configure properly because the source location is dynamic inside the build sandbox
        return projectRoot.relativize(Path.of(absolutePath)).toString();
    }

    @NotNull
    @Override
    public GeneralCommandLine createDriverCommandLine(@NotNull DebuggerDriver driver,
            @NotNull ArchitectureType architectureType) throws ExecutionException {
        GeneralCommandLine commandLine = super.createDriverCommandLine(driver, architectureType);
        commandLine.setWorkDirectory(workingDirectory.toFile());
        return commandLine;
    }
}
