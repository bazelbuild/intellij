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
package com.google.idea.sdkcompat.golang;

import com.goide.dlv.DlvDebugProcess;
import com.intellij.execution.ExecutionResult;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import org.jetbrains.debugger.connection.VmConnection;

/** Adapter to bridge different SDK versions. */
public class DlvDebugProcessCompatUtils {
  /**
   * #api173: changed in 2018.1, remove when 2017.3 no longer supported
   *
   * <p>{@link DlvDebugProcess.MyBreakpointHandler#pauseIfNeededAndProcess} belongs to {@link
   * DlvDebugProcess.MyBreakpointHandler} in 2017.3, but to {@link DlvDebugProcess} later on.
   */
  public static Class<?> classForPauseIfNeededAndProcess(
      Class<?> processClass, Class<?> handlerClass) {
    return handlerClass;
  }

  /**
   * #api173: changed in 2018.1, remove when 2017.3 no longer supported
   *
   * <p>{@link DlvDebugProcess.MyBreakpointHandler#pauseIfNeededAndProcess} belongs to {@link
   * DlvDebugProcess.MyBreakpointHandler} in 2017.3, but to {@link DlvDebugProcess} later on.
   */
  public static Object objectForPauseIfNeededAndProcess(
      XDebugProcess process, XBreakpointHandler<?> breakpointHandler) {
    return breakpointHandler;
  }

  /**
   * #api173: changed in 2018.1, remove when 2017.3 no longer supported
   *
   * <p>Construct a {@link DlvDebugProcess}. Constructor modified in 2018.1.
   */
  public static DlvDebugProcess constructDlvDebugProcess(
      XDebugSession session, VmConnection<?> connection, ExecutionResult er) {
    return new DlvDebugProcess(session, connection, er, /* remote */ true);
  }
}
