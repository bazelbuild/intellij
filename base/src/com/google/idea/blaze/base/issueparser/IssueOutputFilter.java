/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.issueparser;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.console.BlazeConsoleToolWindowFactory;
import com.google.idea.blaze.base.console.BlazeConsoleView;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.ui.problems.BlazeProblemsView;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import javax.annotation.Nullable;

/** Parses issues from blaze output, forwarding to {@link BlazeProblemsView}. */
public class IssueOutputFilter implements Filter {

  private static final Logger logger = Logger.getInstance(IssueOutputFilter.class);

  private final Project project;
  private final BlazeIssueParser issueParser;
  private final boolean linkToBlazeConsole;

  public IssueOutputFilter(
      Project project,
      WorkspaceRoot workspaceRoot,
      BlazeInvocationContext invocationContext,
      boolean linkToBlazeConsole) {
    this(
        project,
        BlazeIssueParser.defaultIssueParsers(project, workspaceRoot, invocationContext),
        linkToBlazeConsole);
  }

  public IssueOutputFilter(
      Project project, ImmutableList<Parser> parsers, boolean linkToBlazeConsole) {
    this.issueParser = new BlazeIssueParser(parsers);
    this.project = project;
    this.linkToBlazeConsole = linkToBlazeConsole;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    IssueOutput issue = issueParser.parseIssue(line);
    if (issue == null) {
      return null;
    }
    logger.warn(issue.toString());
    if (!linkToBlazeConsole) {
      BlazeProblemsView.getInstance(project).addMessage(issue, null);
      return null;
    }
    int offset = entireLength - line.length();
    ResultItem dummyResult = dummyResult(offset);
    BlazeProblemsView.getInstance(project)
        .addMessage(issue, openConsoleToHyperlink(dummyResult.getHyperlinkInfo(), offset));
    return new Result(ImmutableList.of(dummyResult));
  }

  private ResultItem dummyResult(int offset) {
    return new ResultItem(
        offset,
        offset,
        project -> {},
        SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(),
        SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes());
  }

  private Navigatable openConsoleToHyperlink(HyperlinkInfo link, int originalOffset) {
    return new Navigatable() {
      @Override
      public void navigate(boolean requestFocus) {
        BlazeConsoleView consoleView = BlazeConsoleView.getInstance(project);
        ToolWindow toolWindow =
            ToolWindowManager.getInstance(project).getToolWindow(BlazeConsoleToolWindowFactory.ID);
        toolWindow.activate(() -> consoleView.navigateToHyperlink(link, originalOffset), true);
      }

      @Override
      public boolean canNavigate() {
        return true;
      }

      @Override
      public boolean canNavigateToSource() {
        return true;
      }
    };
  }
}
