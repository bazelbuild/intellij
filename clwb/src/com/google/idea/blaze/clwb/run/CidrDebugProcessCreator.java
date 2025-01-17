package com.google.idea.blaze.clwb.run;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.ThrowableComputable;
import com.jetbrains.cidr.execution.CidrCoroutineHelper;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;

public class CidrDebugProcessCreator {
  public static CidrDebugProcess create(ThrowableComputable<CidrDebugProcess, ExecutionException> creator) throws ExecutionException {
    return CidrCoroutineHelper.runOnEDT(creator);
  }
}
