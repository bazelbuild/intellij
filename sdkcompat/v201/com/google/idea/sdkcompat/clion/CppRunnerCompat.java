package com.google.idea.sdkcompat.clion;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.execution.CidrCommandLineState;
import com.jetbrains.cidr.execution.CidrRunner;
import javax.annotation.Nullable;

/** Api compat with 2020.1 #api193 */
public abstract class CppRunnerCompat extends CidrRunner {

  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    // Compatibility of profile is checked in caller (BlazeCommandRunConfiguration)
    if (executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return true;
    }
    return super.canRun(executorId, profile);
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment environment)
      throws ExecutionException {
    if (environment.getExecutor().getId().equals(DefaultDebugExecutor.EXECUTOR_ID)
        && state instanceof CidrCommandLineState) {
      CidrCommandLineState cidrState = (CidrCommandLineState) state;
      XDebugSession debugSession = startDebugSession(cidrState, environment, false);
      return debugSession.getRunContentDescriptor();
    }
    return super.doExecute(state, environment);
  }
}
