package com.google.idea.sdkcompat.java;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.HotSwapProgressImpl;

/** Utility to bridge different SDK versions. */
public class HotSwapCompatUtils {

  public static void setSessionForActions(HotSwapProgressImpl progress, DebuggerSession session) {
    progress.setSessionForActions(session);
  }
}
