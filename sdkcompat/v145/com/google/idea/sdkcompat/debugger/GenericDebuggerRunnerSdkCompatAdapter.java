package com.google.idea.sdkcompat.debugger;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import javax.annotation.Nullable;

/** SDK compatibility for {@link GenericDebuggerRunner}. */
public class GenericDebuggerRunnerSdkCompatAdapter extends GenericDebuggerRunner {

  @Nullable
  protected RunContentDescriptor attachVirtualMachine(
      RunProfileState state,
      ExecutionEnvironment env,
      RemoteConnection connection,
      long pollTimeout)
      throws ExecutionException {
    // no timeout available until 2016.2 onwards
    return super.attachVirtualMachine(
        state, env, connection, pollTimeout != 0 /* pollConnection */);
  }
}
