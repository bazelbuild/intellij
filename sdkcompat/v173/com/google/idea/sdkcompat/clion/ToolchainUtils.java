package com.google.idea.sdkcompat.clion;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.DebuggerKind;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;

/** Handles changes to toolchains between different api versions */
public class ToolchainUtils {
  public static Toolchain getToolchain() {
    return CPPToolchains.getInstance().getDefaultToolchain();
  }

  public static void setDefaultDebuggerPath(String debuggerPath) {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              CPPToolchains cppToolchains = CPPToolchains.getInstance();
              Toolchain toolchain = cppToolchains.getDefaultToolchain();
              if (toolchain != null) {
                cppToolchains.beginUpdate();
                toolchain.setDebuggerKind(DebuggerKind.CUSTOM_GDB);
                toolchain.setCustomGDBExecutablePath(debuggerPath);
                cppToolchains.endUpdate();
                return;
              }

              Toolchain newToolchain = cppToolchains.createDefaultToolchain();
              cppToolchains.beginUpdate();
              cppToolchains.addToolchain(newToolchain);
              cppToolchains.endUpdate();
            });
  }
}
