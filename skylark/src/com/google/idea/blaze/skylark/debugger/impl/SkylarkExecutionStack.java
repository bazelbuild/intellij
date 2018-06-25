/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.PauseReason;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.ThreadPausedState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import javax.annotation.Nullable;
import javax.swing.Icon;

class SkylarkExecutionStack extends XExecutionStack {

  private final SkylarkDebugProcess debugProcess;
  private final ThreadInfo threadInfo;

  SkylarkExecutionStack(SkylarkDebugProcess debugProcess, ThreadInfo threadInfo) {
    super(threadInfo.name, getThreadIcon(threadInfo));
    this.debugProcess = debugProcess;
    this.threadInfo = threadInfo;
  }

  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    return null;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              debugProcess.listFrames(threadInfo.id, container);
            });
  }

  long getThreadId() {
    return threadInfo.id;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SkylarkExecutionStack)) {
      return false;
    }
    return threadInfo.equals(((SkylarkExecutionStack) obj).threadInfo);
  }

  @Override
  public int hashCode() {
    return threadInfo.hashCode();
  }

  private static Icon getThreadIcon(ThreadInfo threadInfo) {
    ThreadPausedState pausedState = threadInfo.getPausedState();
    if (pausedState != null && pausedState.getPauseReason() == PauseReason.HIT_BREAKPOINT) {
      return AllIcons.Debugger.ThreadAtBreakpoint;
    }
    return AllIcons.Debugger.ThreadSuspended;
  }
}
