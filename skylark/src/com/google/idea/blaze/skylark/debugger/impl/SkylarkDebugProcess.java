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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.Breakpoint;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.ContinueExecutionRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.DebugEvent;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.DebugEvent.PayloadCase;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.DebugRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.EvaluateRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.Location;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.PauseThreadRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.SetBreakpointsRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.StartDebuggingRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.Stepping;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.ThreadPausedState;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Core class controlling skylark debugging behavior. Interfaces with an {@link XDebugSession},
 * responsible for the UI and some other state.
 */
public class SkylarkDebugProcess extends XDebugProcess {

  private static final Logger logger = Logger.getInstance(SkylarkDebugProcess.class);

  private final Project project;
  private final ExecutionResult executionResult;
  private final DebugClientTransport transport;

  // state shared with debug server
  private final ConcurrentMap<Location, XLineBreakpoint<XBreakpointProperties>> lineBreakpoints =
      new ConcurrentHashMap<>();
  private final Map<Long, ThreadInfo> threads = new ConcurrentHashMap<>();
  // the currently-stepping thread gets priority in the UI -- we always grab focus when it's paused
  private volatile long currentlySteppingThreadId = 0;

  public SkylarkDebugProcess(XDebugSession session, ExecutionResult executionResult, int port) {
    super(session);
    this.project = session.getProject();
    this.executionResult = executionResult;
    this.transport = new DebugClientTransport(this, port);

    session.setPauseActionSupported(true);
  }

  Collection<ThreadInfo> getThreads() {
    return ImmutableList.copyOf(threads.values());
  }

  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return new XBreakpointHandler[] {new SkylarkLineBreakpointHandler(this)};
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return new SkylarkDebuggerEditorsProvider();
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return executionResult.getProcessHandler();
  }

  @Override
  public ExecutionConsole createConsole() {
    return executionResult.getExecutionConsole();
  }

  @Override
  public void sessionInitialized() {
    waitForConnection();
  }

  private boolean isConnected() {
    return transport.isConnected();
  }

  @Override
  public void stop() {
    // resume all threads prior to stopping debugger
    startStepping(null, Stepping.NONE);
    transport.close();
  }

  private void waitForConnection() {
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Connecting To Debugger", /* canBeCanceled */ false) {
              @Override
              public void run(ProgressIndicator indicator) {
                indicator.setText("Waiting for connection...");
                boolean success = transport.waitForConnection();
                if (!success) {
                  getSession().reportError("Failed to connect to the debugger");
                  transport.close();
                  getSession().stop();
                  return;
                }
                init();
              }
            });
  }

  private void init() {
    getSession().rebuildViews();
    registerBreakpoints();
    assertResponseType(
        transport.sendRequest(
            DebugRequest.newBuilder()
                .setStartDebugging(StartDebuggingRequest.newBuilder().build())),
        PayloadCase.START_DEBUGGING);
  }

  private void registerBreakpoints() {
    SetBreakpointsRequest.Builder request = SetBreakpointsRequest.newBuilder();
    lineBreakpoints.forEach(
        (l, b) -> request.addBreakpoint(Breakpoint.newBuilder().setLocation(l)));
    assertResponseType(
        transport.sendRequest(DebugRequest.newBuilder().setSetBreakpoints(request)),
        PayloadCase.SET_BREAKPOINTS);
  }

  /** If the response doesn't match the expected type, or is an error event, log an error. */
  private void assertResponseType(@Nullable DebugEvent response, PayloadCase expectedType) {
    if (response == null) {
      getSession()
          .reportError(String.format("No '%s' response received from the debugger", expectedType));
      return;
    }
    if (expectedType.equals(response.getPayloadCase())) {
      return;
    }
    if (response.hasError()) {
      handleError(response.getError());
    } else {
      String message =
          String.format(
              "Expected response type '%s', but got '%s'", expectedType, response.getPayloadCase());
      getSession().reportError(message);
    }
  }

  private void handleError(SkylarkDebuggingProtos.Error error) {
    getSession().reportError(error.getMessage());
  }

  private Location convertLocation(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    // TODO(brendandouglas): handle local changes?
    return Location.newBuilder()
        .setLineNumber(breakpoint.getLine() + 1)
        .setPath(breakpoint.getPresentableFilePath())
        .build();
  }

  @Nullable
  private static String getConditionExpression(XBreakpoint<?> breakpoint) {
    return breakpoint.getConditionExpression() == null
        ? null
        : breakpoint.getConditionExpression().getExpression();
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.OVER);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.INTO);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.OUT);
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    // unpausing only a single thread isn't well supported by the debugging API, so pass through a
    // null suspect context, indicating all threads should be unpaused
    startStepping(null, Stepping.NONE);
  }

  private void startStepping(@Nullable XSuspendContext context, Stepping stepping) {
    if (!isConnected()) {
      return;
    }
    ApplicationManager.getApplication()
        .executeOnPooledThread(() -> doStartStepping(context, stepping));
  }

  /** Blocks waiting for a response from the debugger, so must be called on a worker thread. */
  private void doStartStepping(@Nullable XSuspendContext context, Stepping stepping) {
    long threadId = getThreadId(context);
    if (threadId == 0 && stepping != Stepping.NONE) {
      // TODO(brendandouglas): cache suspended threads here, and apply stepping behavior to all?
      return;
    }
    transport.sendRequest(
        DebugRequest.newBuilder()
            .setContinueExecution(
                ContinueExecutionRequest.newBuilder()
                    .setThreadId(threadId)
                    .setStepping(stepping)
                    .build()));

    currentlySteppingThreadId = threadId;

    // this is necessary because we yet aren't reliably informed of thread death. If the thread
    // finishes without triggering the stepping condition, we need to remind the debugger that there
    // could still be suspended threads remaining.
    scheduleWakeupIfNecessary(2000);
  }

  private void scheduleWakeupIfNecessary(int delayMillis) {
    long pausedThreadCount =
        threads.values().stream().filter(t -> t.getPausedState() != null).count();
    if (pausedThreadCount < 2) {
      // if no other threads are paused, the UI state will already be up-to-date
      return;
    }
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError =
        AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(this::wakeUpUiIfNecessary, delayMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void startPausing() {
    if (!isConnected()) {
      return;
    }
    // pause all threads
    transport.sendRequest(
        DebugRequest.newBuilder().setPauseThread(PauseThreadRequest.newBuilder()));
  }

  private long getThreadId(@Nullable XSuspendContext context) {
    if (context instanceof SkylarkSuspendContext) {
      return ((SkylarkSuspendContext) context).getActiveExecutionStack().getThreadId();
    }
    return 0;
  }

  void addBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    lineBreakpoints.put(convertLocation(breakpoint), breakpoint);
    if (isConnected()) {
      registerBreakpoints();
    }
  }

  void removeBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    boolean changed = lineBreakpoints.remove(convertLocation(breakpoint)) != null;
    if (changed && isConnected()) {
      registerBreakpoints();
    }
  }

  /**
   * Sends an {@link EvaluateRequest} to the debug server for the current frame, passing the
   * response on to the {@link XEvaluationCallback}.
   */
  void evaluate(String expression, XEvaluationCallback callback) {
    try {
      evaluate(currentFrame().threadId, expression, callback);
    } catch (SkylarkDebuggerException e) {
      callback.errorOccurred(e.getMessage());
    }
  }

  private void evaluate(long threadId, String expression, XEvaluationCallback callback) {
    EvaluateRequest request =
        EvaluateRequest.newBuilder().setThreadId(threadId).setExpression(expression).build();
    doEvaluate(request, callback);
  }

  private SkylarkStackFrame currentFrame() throws SkylarkDebuggerException {
    if (!isConnected()) {
      getSession().stop();
      throw new SkylarkDebuggerException("Disconnected");
    }
    SkylarkStackFrame frame = (SkylarkStackFrame) getSession().getCurrentStackFrame();
    if (frame == null) {
      throw new RuntimeException("Process is running");
    }
    return frame;
  }

  private void doEvaluate(EvaluateRequest request, XEvaluationCallback callback) {
    DebugEvent response = transport.sendRequest(DebugRequest.newBuilder().setEvaluate(request));
    if (response == null) {
      callback.errorOccurred("No response from the Skylark debugger");
      return;
    }
    if (response.hasError()) {
      callback.errorOccurred(response.getError().getMessage());
      return;
    }
    checkState(response.getPayloadCase() == PayloadCase.EVALUATE);
    callback.evaluated(SkylarkDebugValue.fromProto(response.getEvaluate().getResult()));
  }

  void listFrames(long threadId, XExecutionStack.XStackFrameContainer container) {
    DebugEvent response =
        transport.sendRequest(
            DebugRequest.newBuilder()
                .setListFrames(
                    SkylarkDebuggingProtos.ListFramesRequest.newBuilder().setThreadId(threadId)));
    if (response == null) {
      container.errorOccurred("No frames data received from the Skylark debugger");
      return;
    }
    if (response.hasError()) {
      container.errorOccurred(response.getError().getMessage());
      return;
    }
    checkState(response.getPayloadCase() == PayloadCase.LIST_FRAMES);
    List<SkylarkDebuggingProtos.Frame> frames = response.getListFrames().getFrameList();
    container.addStackFrames(
        frames.stream().map(f -> convert(threadId, f)).collect(Collectors.toList()), true);
  }

  private SkylarkStackFrame convert(long threadId, SkylarkDebuggingProtos.Frame frame) {
    return new SkylarkStackFrame(this, threadId, frame);
  }

  void handleEvent(SkylarkDebuggingProtos.DebugEvent event) {
    switch (event.getPayloadCase()) {
      case ERROR:
        handleError(event.getError());
        return;
      case THREAD_STARTED:
        addOrUpdateThread(event.getThreadStarted().getThread());
        return;
      case THREAD_PAUSED:
        handleThreadPausedEvent(event.getThreadPaused().getThread());
        return;
      case THREAD_CONTINUED:
        addOrUpdateThread(event.getThreadContinued().getThread());
        return;
      case THREAD_ENDED:
        threads.remove(event.getThreadEnded().getThread().getId());
        wakeUpUiIfNecessary();
        return;
      case LIST_THREADS:
      case LIST_FRAMES:
      case EVALUATE:
      case SET_BREAKPOINTS:
      case CONTINUE_EXECUTION:
      case START_DEBUGGING:
      case PAUSE_THREAD:
        logger.error("Can't handle a response event without the associated request");
        return;
      case PAYLOAD_NOT_SET:
        break; // intentional fall through to error reporting
    }
    getSession()
        .reportError(
            "Unrecognized or unset skylark debugger response type. Try upgrading to a newer "
                + "version of the plugin.");
  }

  /**
   * The debugger UI doesn't handle multiple concurrently paused threads well. If the 'primary'
   * thread dies, or never resumes, we need to manually remind it that there may be other suspended
   * threads remaining.
   *
   * <p>See RemoteDebugger#processThreadEvent for an upstream example.
   */
  private void wakeUpUiIfNecessary() {
    if (getSession().isSuspended()) {
      // we already have an active thread
      return;
    }
    getThreads()
        .stream()
        .filter(t -> t.getPausedState() != null)
        .findFirst()
        .ifPresent(t -> notifyThreadPaused(t, true));
  }

  private ThreadInfo toThreadInfo(SkylarkDebuggingProtos.Thread thread) {
    return new ThreadInfo(
        thread.getId(),
        thread.getName(),
        thread.hasThreadPausedState() ? thread.getThreadPausedState() : null);
  }

  private ThreadInfo addOrUpdateThread(SkylarkDebuggingProtos.Thread thread) {
    ThreadInfo info = threads.computeIfAbsent(thread.getId(), id -> toThreadInfo(thread));
    info.updatePausedState(thread.hasThreadPausedState() ? thread.getThreadPausedState() : null);
    return info;
  }

  private void handleThreadPausedEvent(SkylarkDebuggingProtos.Thread thread) {
    ThreadPausedState pausedState = Preconditions.checkNotNull(thread.getThreadPausedState());
    if (pausedState.getPauseReason() != SkylarkDebuggingProtos.PauseReason.HIT_BREAKPOINT) {
      notifyThreadPaused(addOrUpdateThread(thread), false);
      return;
    }
    XLineBreakpoint<XBreakpointProperties> breakpoint =
        lineBreakpoints.get(pausedState.getLocation().toBuilder().setColumnNumber(0).build());
    if (breakpoint == null) {
      notifyThreadPaused(addOrUpdateThread(thread), false);
    } else {
      pauseIfConditionSatisfied(thread, breakpoint);
    }
  }

  /**
   * The thread is paused server-side. Check whether the breakpoint conditional is satisfied, if
   * present, and either notify the UI that the thread is paused, or instruct the server to resume
   * the thread.
   *
   * <p>TODO(brendandouglas): if this is too slow, go back to doing this on the server side
   */
  private void pauseIfConditionSatisfied(
      SkylarkDebuggingProtos.Thread thread, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    XExpression xExpression = breakpoint.getConditionExpression();
    String expr = xExpression == null ? null : xExpression.getExpression();
    if (StringUtil.isEmptyOrSpaces(expr)) {
      notifyThreadPaused(addOrUpdateThread(thread), false);
      return;
    }
    // a little hacky, but lets Skylark handle the 'toBoolean' logic (see EvalUtils#toBoolean)
    String wrappedExpr = String.format("True if (%s) else False", expr);
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () ->
                evaluate(
                    thread.getId(),
                    wrappedExpr,
                    new ConditionalBreakpointCallback(breakpoint, thread)));
  }

  private void notifyThreadPaused(ThreadInfo info, boolean alwaysNotify) {
    ThreadPausedState pausedState = Preconditions.checkNotNull(info.getPausedState());
    XLineBreakpoint<XBreakpointProperties> breakpoint =
        lineBreakpoints.get(pausedState.getLocation().toBuilder().setColumnNumber(0).build());
    SkylarkSuspendContext suspendContext = new SkylarkSuspendContext(this, info);
    if (breakpoint != null) {
      getSession().breakpointReached(breakpoint, null, suspendContext);
    } else if (alwaysNotify
        || info.id == currentlySteppingThreadId
        || !getSession().isSuspended()
        || individualThreadPausedByUser(pausedState.getPauseReason())) {
      getSession().positionReached(suspendContext);
    }
  }

  private boolean individualThreadPausedByUser(SkylarkDebuggingProtos.PauseReason reason) {
    switch (reason) {
      case STEPPING:
      case PAUSE_THREAD_REQUEST:
      case HIT_BREAKPOINT:
        return true;
      case ALL_THREADS_PAUSED:
      case UNSET:
        return false;
      case UNRECOGNIZED:
    }
    getSession()
        .reportError("Unrecognized pause reason. Try upgrading to a newer version of the plugin.");
    // default to returning true, so we don't leave the debugger in an unusable state
    return true;
  }

  private class ConditionalBreakpointCallback implements XEvaluationCallback {

    private final XLineBreakpoint<XBreakpointProperties> breakpoint;
    private final SkylarkDebuggingProtos.Thread thread;

    ConditionalBreakpointCallback(
        XLineBreakpoint<XBreakpointProperties> breakpoint, SkylarkDebuggingProtos.Thread thread) {
      this.breakpoint = breakpoint;
      this.thread = thread;
    }

    @Override
    public void evaluated(XValue result) {
      if (isTrue((SkylarkDebugValue) result)) {
        stopAtBreakpoint();
      } else {
        resumeThread();
      }
    }

    private void stopAtBreakpoint() {
      notifyThreadPaused(addOrUpdateThread(thread), false);
    }

    private void resumeThread() {
      transport.sendRequest(
          DebugRequest.newBuilder()
              .setContinueExecution(
                  ContinueExecutionRequest.newBuilder()
                      .setThreadId(thread.getId())
                      .setStepping(Stepping.NONE)
                      .build()));
    }

    private boolean isTrue(SkylarkDebugValue result) {
      return "True".equals(result.value.getDescription());
    }

    @Override
    public void errorOccurred(String errorMessage) {
      String title = "Breakpoint Condition Error";
      String message =
          String.format(
              "Breakpoint: %s\nError: %s\nWould you like to stop at the breakpoint?",
              breakpoint.getType().getDisplayText(breakpoint), errorMessage);
      Ref<Boolean> stop = new Ref<>(true);
      ApplicationManager.getApplication()
          .invokeAndWait(
              () ->
                  stop.set(
                      Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
                          == Messages.YES));
      if (stop.get()) {
        stopAtBreakpoint();
      } else {
        resumeThread();
      }
    }
  }
}
