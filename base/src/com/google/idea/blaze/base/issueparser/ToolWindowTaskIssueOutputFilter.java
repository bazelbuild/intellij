/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.issueparser;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.io.AbsolutePathPatcher.AbsolutePathPatcherUtil;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowService;
import com.google.idea.blaze.base.ui.problems.BuildTasksProblemsView;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Parses issues from blaze output, forwarding to {@link TasksToolWindowService}.
 *
 * <p>Copy and replacement of {@link IssueOutputFilter}.
 */
public class ToolWindowTaskIssueOutputFilter implements Filter {

  private static final Logger logger =
      Logger.getInstance(com.google.idea.blaze.base.issueparser.IssueOutputFilter.class);

  private final Project project;
  private final BlazeIssueParser issueParser;
  private final boolean linkToBlazeConsole;
  private final Task task;

  public ToolWindowTaskIssueOutputFilter(
      Project project,
      WorkspaceRoot workspaceRoot,
      BlazeInvocationContext.ContextType invocationContext,
      Task task) {
    this(
        project,
        BlazeIssueParser.defaultIssueParsers(project, workspaceRoot, invocationContext),
        task,
        true);
  }

  public ToolWindowTaskIssueOutputFilter(
      Project project, ImmutableList<Parser> parsers, Task task, boolean linkToBlazeConsole) {
    this.issueParser = new BlazeIssueParser(parsers);
    this.project = project;
    this.linkToBlazeConsole = linkToBlazeConsole;
    this.task = task;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    IssueOutput issue = issueParser.parseIssue(line);
    if (issue == null) {
      return null;
    }
    logger.warn(issue.toString());
    List<ResultItem> links = new ArrayList<>();
    int offset = entireLength - line.length();
    if (linkToBlazeConsole) {
      ResultItem dummyResult = dummyResult(offset);
      BuildTasksProblemsView.getInstance(project)
          .addMessage(issue, openConsoleToHyperlink(dummyResult.getHyperlinkInfo(), offset));
      links.add(dummyResult);
    } else {
      BuildTasksProblemsView.getInstance(project).addMessage(issue, null);
    }
    ResultItem hyperlink = hyperlinkItem(issue, offset);
    if (hyperlink != null) {
      links.add(hyperlink);
    }
    return !links.isEmpty() ? new Result(links) : null;
  }

  /** A dummy result used for navigating from the problems view to the console. */
  private static ResultItem dummyResult(int offset) {
    return new ResultItem(
        offset,
        offset,
        (NonProblemHyperlinkInfo) project -> {},
        SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(),
        SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes());
  }

  /**
   * A user-visible hyperlink navigating from the console to the relevant file + line of the issue.
   */
  @Nullable
  private static ResultItem hyperlinkItem(IssueOutput issue, int offset) {
    TextRange range = issue.getConsoleHyperlinkRange();
    HyperlinkInfo link = getHyperlinkInfo(issue);
    if (range == null || link == null) {
      return null;
    }
    return new ResultItem(range.getStartOffset() + offset, range.getEndOffset() + offset, link);
  }

  @Nullable
  private static HyperlinkInfo getHyperlinkInfo(IssueOutput issue) {
    Navigatable navigatable = issue.getNavigatable();
    if (navigatable != null) {
      return project -> navigatable.navigate(true);
    }
    VirtualFile vf = resolveVirtualFile(issue.getFile());
    return vf != null
        ? project ->
            new OpenFileDescriptor(project, vf, issue.getLine() - 1, issue.getColumn() - 1)
                .navigate(true)
        : null;
  }

  /**
   * Finds the virtual file associated with the given file path, resolving symlinks where relevant.
   */
  @Nullable
  private static VirtualFile resolveVirtualFile(@Nullable File file) {
    if (file == null) {
      return null;
    }
    VirtualFile vf = VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ true);
    return vf != null ? resolveSymlinks(vf) : null;
  }

  /**
   * Attempts to resolve symlinks in the virtual file path, falling back to returning the original
   * virtual file if unsuccessful.
   */
  private static VirtualFile resolveSymlinks(VirtualFile file) {
    VirtualFile resolved =
        AbsolutePathPatcherUtil.fixPath(file.getCanonicalFile(), /* refreshIfNeeded= */ false);
    return resolved != null ? resolved : file;
  }

  private Navigatable openConsoleToHyperlink(HyperlinkInfo link, int originalOffset) {
    return new Navigatable() {
      @Override
      public void navigate(boolean requestFocus) {
        TasksToolWindowService.getInstance(project).navigate(task, link, originalOffset);
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
