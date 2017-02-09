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
package com.google.idea.blaze.base.scope.scopes;

import com.google.idea.blaze.base.console.BlazeConsoleService;
import com.google.idea.blaze.base.console.ColoredConsoleStream;
import com.google.idea.blaze.base.console.ConsoleStream;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Moves print output to the blaze console. */
public class BlazeConsoleScope implements BlazeScope {

  /** Builder for blaze console scope */
  public static class Builder {
    private Project project;
    private ProgressIndicator progressIndicator;
    private boolean suppressConsole = false;
    private boolean escapeAnsiColorCodes = false;

    public Builder(@NotNull Project project) {
      this(project, null);
    }

    public Builder(@NotNull Project project, ProgressIndicator progressIndicator) {
      this.project = project;
      this.progressIndicator = progressIndicator;
    }

    public Builder setSuppressConsole(boolean suppressConsole) {
      this.suppressConsole = suppressConsole;
      return this;
    }

    public Builder escapeAnsiColorcodes(boolean escapeAnsiColorCodes) {
      this.escapeAnsiColorCodes = escapeAnsiColorCodes;
      return this;
    }

    public BlazeConsoleScope build() {
      return new BlazeConsoleScope(
          project, progressIndicator, suppressConsole, escapeAnsiColorCodes);
    }
  }

  @NotNull private final BlazeConsoleService blazeConsoleService;

  @Nullable private final ProgressIndicator progressIndicator;

  private final boolean showDialogOnChange;
  private boolean activated;

  private final ConsoleStream consoleStream;

  private OutputSink<PrintOutput> printSink =
      (output) -> {
        @NotNull String text = output.getText();
        @NotNull
        ConsoleViewContentType contentType =
            output.getOutputType() == OutputType.ERROR
                ? ConsoleViewContentType.ERROR_OUTPUT
                : ConsoleViewContentType.NORMAL_OUTPUT;
        print(text, contentType);
        return OutputSink.Propagation.Continue;
      };

  private OutputSink<StatusOutput> statusSink =
      (output) -> {
        @NotNull String text = output.getStatus();
        @NotNull ConsoleViewContentType contentType = ConsoleViewContentType.NORMAL_OUTPUT;
        print(text, contentType);
        return OutputSink.Propagation.Continue;
      };

  private BlazeConsoleScope(
      @NotNull Project project,
      @Nullable ProgressIndicator progressIndicator,
      boolean suppressConsole,
      boolean escapeAnsiColorCodes) {
    this.blazeConsoleService = BlazeConsoleService.getInstance(project);
    this.progressIndicator = progressIndicator;
    this.showDialogOnChange = !suppressConsole;
    ConsoleStream sinkConsoleStream = blazeConsoleService::print;
    this.consoleStream =
        escapeAnsiColorCodes ? new ColoredConsoleStream(sinkConsoleStream) : sinkConsoleStream;
  }

  private void print(String text, ConsoleViewContentType contentType) {
    consoleStream.print(text, contentType);
    consoleStream.print("\n", contentType);

    if (showDialogOnChange && !activated) {
      activated = true;
      ApplicationManager.getApplication().invokeLater(blazeConsoleService::activateConsoleWindow);
    }
  }

  @Override
  public void onScopeBegin(@NotNull final BlazeContext context) {
    context.addOutputSink(PrintOutput.class, printSink);
    context.addOutputSink(StatusOutput.class, statusSink);
    blazeConsoleService.clear();
    blazeConsoleService.setStopHandler(
        () -> {
          if (progressIndicator != null) {
            progressIndicator.cancel();
          }
          context.setCancelled();
        });
  }

  @Override
  public void onScopeEnd(@NotNull BlazeContext context) {
    blazeConsoleService.setStopHandler(null);
  }
}
