package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.clwb.ToolchainUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.ArchitectureType;
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionLLDBDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class BlazeLLDBDriverConfiguration extends CLionLLDBDriverConfiguration {
    private final Path workingDirectory;

    public BlazeLLDBDriverConfiguration(@NotNull Project project, Path workingDirectory) {
        super(project, ToolchainUtils.getToolchain());
        this.workingDirectory = workingDirectory;
    }

    @NotNull
    @Override
    public GeneralCommandLine createDriverCommandLine(@NotNull DebuggerDriver driver, @NotNull ArchitectureType architectureType) throws ExecutionException {
        GeneralCommandLine commandLine = super.createDriverCommandLine(driver, architectureType);
        commandLine.setWorkDirectory(this.workingDirectory.toFile());
        return commandLine;
    }
}
