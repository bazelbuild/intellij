package com.google.idea.sdkcompat.golang;

import com.goide.dlv.DlvDebugProcess;
import com.intellij.execution.ExecutionResult;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.debugger.connection.VmConnection;

/** Adapter to bridge different SDK versions. */
public class DlvDebugProcessCompatUtils {

  /** Construct a {@link DlvDebugProcess}. Constructor modified in 2018.1. */
  public static DlvDebugProcess constructDlvDebugProcess(
      XDebugSession session, VmConnection<?> connection, ExecutionResult er) {
    return new DlvDebugProcess(session, connection, er, /* remote */ true);
  }
}
