/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.processhandler;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.util.Key;

/**
 * Scoped process handler.
 *
 * A context is created during construction and is ended when the process is terminated.
 */
public final class ScopedBlazeProcessHandler extends KillableColoredProcessHandler {
  /**
   * Methods to give the caller of {@link ScopedBlazeProcessHandler} hooks after the context is created.
   */
  public interface ScopedProcessHandlerDelegate {
    /**
     * This method is called when the process starts. Any context setup (like pushing scopes on the context) should be done here.
     */
    void onBlazeContextStart(BlazeContext context);

    /**
     * Get a list of process listeners to add to the process.
     */
    ImmutableList<ProcessListener> createProcessListeners(BlazeContext context);

  }

  private final ScopedProcessHandlerDelegate scopedProcessHandlerDelegate;
  private final BlazeContext context;

  /**
   * Construct a process handler and a context to be used for the life of the process.
   *
   * @param blazeCommand the blaze command to run
   * @param workspaceRoot workspace root
   * @param scopedProcessHandlerDelegate delegate methods that will be run with the process's context.
   * @throws ExecutionException
   */
  public ScopedBlazeProcessHandler(
    BlazeCommand blazeCommand,
    WorkspaceRoot workspaceRoot,
    ScopedProcessHandlerDelegate scopedProcessHandlerDelegate) throws ExecutionException {
    super(new GeneralCommandLine(blazeCommand.toList()).withWorkDirectory(workspaceRoot.directory().getPath()));

    this.scopedProcessHandlerDelegate = scopedProcessHandlerDelegate;
    this.context = new BlazeContext();
    // The context is released in the ScopedProcessHandlerListener.
    this.context.hold();

    for (ProcessListener processListener : scopedProcessHandlerDelegate.createProcessListeners(context)) {
      addProcessListener(processListener);
    }
    addProcessListener(new ScopedProcessHandlerListener());
  }

  @Override
  public void coloredTextAvailable(String text, Key attributes) {
    // Change blaze's stderr output to normal color, otherwise
    // test output looks red
    if (attributes == ProcessOutputTypes.STDERR) {
      attributes = ProcessOutputTypes.STDOUT;
    }

    super.coloredTextAvailable(text, attributes);
  }

  /**
   * Handle the {@link BlazeContext} held in a {@link ScopedBlazeProcessHandler}. This class will take care of calling methods when the
   * process starts and freeing the context when the process terminates.
   */
  private class ScopedProcessHandlerListener extends ProcessAdapter {

    @Override
    public void startNotified(ProcessEvent event) {
      scopedProcessHandlerDelegate.onBlazeContextStart(context);
    }

    @Override
    public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      context.release();
    }
  }
}
