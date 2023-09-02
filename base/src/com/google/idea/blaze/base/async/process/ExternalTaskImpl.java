/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.process;

import com.google.idea.async.process.CommandLineTask;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** A default implementation of {@link ExternalTask}. */
public class ExternalTaskImpl extends CommandLineTask implements ExternalTask {

  @Nullable private final BlazeContext parentContext;
  private final boolean ignoreExitCode;

  ExternalTaskImpl(
      @Nullable BlazeContext context,
      File workingDirectory,
      List<String> command,
      Map<String, String> environmentVariables,
      @Nullable OutputStream stdout,
      @Nullable OutputStream stderr,
      boolean redirectErrorStream,
      boolean ignoreExitCode) {
    super(workingDirectory, command, environmentVariables, stdout, stderr, redirectErrorStream);
    this.parentContext = context;
    this.ignoreExitCode = ignoreExitCode;
  }

  @Override
  public int run(BlazeScope... scopes) {
    Integer returnValue =
        Scope.push(
            parentContext,
            context -> {
              for (BlazeScope scope : scopes) {
                context.push(scope);
              }
              try {
                if (context.isEnding()) {
                  return -1;
                }
                String logMessage = "Command: " + ParametersListUtil.join(command);

                context.output(
                    PrintOutput.log(
                        StringUtil.shortenTextWithEllipsis(
                            logMessage, /* maxLength= */ 1000, /* suffixLength= */ 0)));

                int exitValue = invokeCommand();
                if (!ignoreExitCode && exitValue != 0) {
                  context.setHasError();
                }
                return exitValue;
              } catch (IOException e) {
                IssueOutput.error(e.getMessage()).submit(context);
                return -1;
              } catch (InterruptedException e) {
                // Logging a ProcessCanceledException is an IJ error - mark context canceled
                // instead
                context.setCancelled();
              }
              return -1;
            });
    return returnValue != null ? returnValue : -1;
  }
}
