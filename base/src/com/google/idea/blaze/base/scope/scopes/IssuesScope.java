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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/** Shows the compiler output. */
public class IssuesScope implements BlazeScope, OutputSink<IssueOutput> {

  private final Project project;
  private final UUID sessionId;
  private final IssueBuffer issueBuffer = new IssueBuffer();

  public IssuesScope(Project project) {
    this.project = project;
    this.sessionId = UUID.randomUUID();
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(IssueOutput.class, this);
    BlazeProblemsView blazeProblemsView = BlazeProblemsView.getInstance(project);
    if (blazeProblemsView != null) {
      blazeProblemsView.clearOldMessages(sessionId);
    }
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (!issueBuffer.isEmpty()) {
      BlazeProblemsView blazeProblemsView = BlazeProblemsView.getInstance(project);
      if (blazeProblemsView != null) {
        issueBuffer.flushBuffer(blazeProblemsView);
      }
      ApplicationManager.getApplication().invokeLater(this::focusProblemsView);
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
  public Propagation onOutput(IssueOutput output) {
    BlazeProblemsView blazeProblemsView = BlazeProblemsView.getInstance(project);
    issueBuffer.addOrBufferIssue(blazeProblemsView, output);
    return Propagation.Continue;
  }

  /**
   * Buffer to limit problems sent to {@link BlazeProblemsView}. Too many can freeze the IDE.
   *
   * <p>Stream out the first {@link #NUM_ISSUES_BEFORE_BUFFER} so that users can see them
   * immediately, but buffer the rest so that we can limit the total number of issues written.
   */
  private class IssueBuffer {
    private static final int NUM_ISSUES_BEFORE_BUFFER = 500;
    private static final int MAX_ISSUES = 4000; // 80,000 is too slow (but benchmark)
    int issuesCount;
    final List<IssueOutput> buffer = new ArrayList<>();

    void addOrBufferIssue(@Nullable BlazeProblemsView blazeProblemsView, IssueOutput output) {
      issuesCount++;
      if (blazeProblemsView == null) {
        return;
      }
      if (issuesCount <= NUM_ISSUES_BEFORE_BUFFER) {
        blazeProblemsView.addMessage(output, sessionId);
      } else {
        buffer.add(output);
      }
    }

    boolean isEmpty() {
      return issuesCount == 0;
    }

    private void flushBuffer(BlazeProblemsView blazeProblemsView) {
      if (issuesCount < MAX_ISSUES) {
        flushIssues(blazeProblemsView, buffer);
        return;
      }
      int numIssuesToFlush = MAX_ISSUES - NUM_ISSUES_BEFORE_BUFFER;
      List<IssueOutput> subList = buffer.subList(buffer.size() - numIssuesToFlush, buffer.size());
      flushIssues(blazeProblemsView, subList);
      IssueOutput truncationIssue =
          IssueOutput.warn(
                  String.format(
                      "Too many problems found. Truncating from %d down to %d (first %d, last %d)",
                      issuesCount, MAX_ISSUES, NUM_ISSUES_BEFORE_BUFFER, numIssuesToFlush))
              .build();
      blazeProblemsView.addMessage(truncationIssue, sessionId);
    }

    private void flushIssues(BlazeProblemsView problemsView, List<IssueOutput> bufferedIssues) {
      for (IssueOutput issueOutput : bufferedIssues) {
        problemsView.addMessage(issueOutput, sessionId);
      }
    }
  }
}
