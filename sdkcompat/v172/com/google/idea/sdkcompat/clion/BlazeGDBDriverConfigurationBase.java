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
