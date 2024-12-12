package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.ArchitectureType;
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionLLDBDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import org.jetbrains.annotations.NotNull;

public class BlazeLLDBDriverConfiguration extends CLionLLDBDriverConfiguration {
    private final WorkspaceRoot workspaceRoot;

    public BlazeLLDBDriverConfiguration(@NotNull Project project, WorkspaceRoot workspaceRoot) {
        super(project, ToolchainUtils.getToolchain());
        this.workspaceRoot = workspaceRoot;
    }

    @NotNull
    @Override
    public GeneralCommandLine createDriverCommandLine(@NotNull DebuggerDriver driver, @NotNull ArchitectureType architectureType) throws ExecutionException {
        GeneralCommandLine commandLine = super.createDriverCommandLine(driver, architectureType);
        commandLine.setWorkDirectory(this.workspaceRoot.directory());
        return commandLine;
    }
}
