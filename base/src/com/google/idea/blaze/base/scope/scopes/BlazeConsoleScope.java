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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.console.BlazeConsoleService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.BlazeUserSettings.BlazeConsolePopupBehavior;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Moves print output to the blaze console. */
public class BlazeConsoleScope implements BlazeScope {

  /** Builder for blaze console scope */
  public static class Builder {
    private Project project;
    private ProgressIndicator progressIndicator;
    private BlazeConsolePopupBehavior popupBehavior = BlazeConsolePopupBehavior.ON_ERROR;
    private final ImmutableList.Builder<Filter> filters = ImmutableList.builder();

    public Builder(Project project) {
      this(project, null);
    }

    public Builder(Project project, ProgressIndicator progressIndicator) {
      this.project = project;
      this.progressIndicator = progressIndicator;
    }

    public Builder setPopupBehavior(BlazeConsolePopupBehavior popupBehavior) {
      this.popupBehavior = popupBehavior;
      return this;
    }

    public Builder addConsoleFilters(Filter... filters) {
      this.filters.add(filters);
      return this;
    }

    public BlazeConsoleScope build() {
      return new BlazeConsoleScope(project, progressIndicator, popupBehavior, filters.build());
    }
  }

  private final BlazeConsoleService blazeConsoleService;
  @Nullable private final ProgressIndicator progressIndicator;
  private final BlazeConsolePopupBehavior popupBehavior;
  private final ImmutableList<Filter> customFilters;

  private boolean activated;

  private OutputSink<PrintOutput> printSink =
      (output) -> {
        String text = output.getText();

        ConsoleViewContentType contentType =
            output.getOutputType() == OutputType.ERROR
                ? ConsoleViewContentType.ERROR_OUTPUT
                : ConsoleViewContentType.NORMAL_OUTPUT;
        print(text, contentType);
        return OutputSink.Propagation.Continue;
      };

  private OutputSink<StatusOutput> statusSink =
      (output) -> {
        String text = output.getStatus();
        ConsoleViewContentType contentType = ConsoleViewContentType.NORMAL_OUTPUT;
        print(text, contentType);
        return OutputSink.Propagation.Continue;
      };

  private BlazeConsoleScope(
      Project project,
      @Nullable ProgressIndicator progressIndicator,
      BlazeConsolePopupBehavior popupBehavior,
      ImmutableList<Filter> customFilters) {
    this.blazeConsoleService = BlazeConsoleService.getInstance(project);
    this.progressIndicator = progressIndicator;
    this.popupBehavior = popupBehavior;
    this.customFilters = customFilters;
  }

  private void print(String text, ConsoleViewContentType contentType) {
    blazeConsoleService.print(text, contentType);
    blazeConsoleService.print("\n", contentType);

    if (activated) {
      return;
    }
    boolean activate =
        popupBehavior == BlazeConsolePopupBehavior.ALWAYS
            || (popupBehavior == BlazeConsolePopupBehavior.ON_ERROR
                && contentType == ConsoleViewContentType.ERROR_OUTPUT);
    if (activate) {
      activated = true;
      ApplicationManager.getApplication().invokeLater(blazeConsoleService::activateConsoleWindow);
    }
  }

  @Override
  public void onScopeBegin(final BlazeContext context) {
    context.addOutputSink(PrintOutput.class, printSink);
    context.addOutputSink(StatusOutput.class, statusSink);
    blazeConsoleService.clear();
    blazeConsoleService.setCustomFilters(customFilters);
    blazeConsoleService.setStopHandler(
        () -> {
          if (progressIndicator != null) {
            progressIndicator.cancel();
          }
          context.setCancelled();
        });
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    blazeConsoleService.setStopHandler(null);
  }
}
