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

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.ui.BlazeProblemsView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** Shows the compiler output. */
public class IssuesScope implements BlazeScope, OutputSink<IssueOutput> {

  private final Project project;
  private final UUID sessionId;
  private int issuesCount;

  public IssuesScope(@NotNull Project project) {
    this.project = project;
    this.sessionId = UUID.randomUUID();
  }

  @Override
  public void onScopeBegin(@NotNull BlazeContext context) {
    context.addOutputSink(IssueOutput.class, this);
    BlazeProblemsView blazeProblemsView = BlazeProblemsView.getInstance(project);
    if (blazeProblemsView != null) {
      blazeProblemsView.clearOldMessages(sessionId);
    }
  }

  @Override
  public void onScopeEnd(@NotNull BlazeContext context) {
    if (issuesCount > 0) {
      ApplicationManager.getApplication()
          .invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  focusProblemsView();
                }
              });
    }
  }

  private void focusProblemsView() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow("Problems");
    if (toolWindow != null) {
      toolWindow.activate(null, false, false);
    }
  }

  @Override
  public Propagation onOutput(@NotNull IssueOutput output) {
    BlazeProblemsView blazeProblemsView = BlazeProblemsView.getInstance(project);
    if (blazeProblemsView != null) {
      blazeProblemsView.addMessage(output, sessionId);
    }
    ++issuesCount;
    return Propagation.Continue;
  }
}
