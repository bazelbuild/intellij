/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.problemsview;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.ui.BlazeProblemsView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/** CLion has no built-in 'Problems' view, so we mostly duplicate the IntelliJ code here. */
public class BlazeProblemsViewConsole implements BlazeProblemsView {
  private static final Logger LOG = Logger.getInstance(BlazeProblemsViewConsole.class);

  private static final String BLAZE_PROBLEMS_TOOLWINDOW_ID = "Blaze Problems";
  private static final EnumSet<ErrorTreeElementKind> ALL_MESSAGE_KINDS =
      EnumSet.allOf(ErrorTreeElementKind.class);

  private final ProblemsViewPanel myPanel;
  private final ExecutorService myViewUpdater =
      new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, 1);
  private final Icon myActiveIcon = AllIcons.Toolwindows.Problems;
  private final Icon myPassiveIcon = IconLoader.getDisabledIcon(myActiveIcon);

  private final Project myProject;

  public static BlazeProblemsViewConsole getImpl(Project project) {
    BlazeProblemsViewConsole blazeProblemsViewConsole =
        (BlazeProblemsViewConsole) ServiceManager.getService(project, BlazeProblemsView.class);
    LOG.assertTrue(blazeProblemsViewConsole != null);
    return blazeProblemsViewConsole;
  }

  public BlazeProblemsViewConsole(final Project project) {
    myProject = project;
    myPanel = new ProblemsViewPanel(project);
    Disposer.register(project, () -> Disposer.dispose(myPanel));
    updateIcon();
  }

  public void createToolWindowContent(ToolWindow toolWindow) {
    final Content content = ContentFactory.SERVICE.getInstance().createContent(myPanel, "", false);
    toolWindow.getContentManager().addContent(content);
    Disposer.register(myProject, () -> toolWindow.getContentManager().removeAllContents(true));
    updateIcon();
  }

  @Override
  public final void addMessage(IssueOutput issue, @NotNull UUID sessionId) {
    final VirtualFile file =
        issue.getFile() != null
            ? VfsUtil.findFileByIoFile(issue.getFile(), true /* refresh */)
            : null;
    Navigatable navigatable = issue.getNavigatable();
    if (navigatable == null && file != null) {
      // convert 1-indexed line/column numbers to 0-indexed
      navigatable =
          new OpenFileDescriptor(myProject, file, issue.getLine() - 1, issue.getColumn() - 1);
    }
    final IssueOutput.Category category = issue.getCategory();
    final int type = translateCategory(category);
    final String[] text = convertMessage(issue);
    final String groupName = file != null ? file.getPresentableUrl() : category.name();
    addMessage(
        type,
        text,
        groupName,
        navigatable,
        getExportTextPrefix(issue),
        getRenderTextPrefix(issue),
        sessionId);
  }

  private static int translateCategory(IssueOutput.Category category) {
    switch (category) {
      case ERROR:
        return MessageCategory.ERROR;
      case WARNING:
        return MessageCategory.WARNING;
      case STATISTICS:
        return MessageCategory.STATISTICS;
      case INFORMATION:
        return MessageCategory.INFORMATION;
      default:
        LOG.error("Unknown message category: " + category);
        return 0;
    }
  }

  private static String[] convertMessage(final IssueOutput issue) {
    String text = issue.getMessage();
    if (!text.contains("\n")) {
      return new String[] {text};
    }
    final List<String> lines = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(text, "\n", false);
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    return ArrayUtil.toStringArray(lines);
  }

  private static String getExportTextPrefix(IssueOutput issue) {
    int line = issue.getLine();
    if (line >= 0) {
      return String.format("line: %d", line);
    }
    return "";
  }

  private static String getRenderTextPrefix(IssueOutput issue) {
    int line = issue.getLine();
    if (line >= 0) {
      return String.format("(%d, %d)", line, issue.getColumn());
    }
    return "";
  }

  @Override
  public void clearOldMessages(@NotNull final UUID currentSessionId) {
    myViewUpdater.execute(
        new Runnable() {
          @Override
          public void run() {
            cleanupChildrenRecursively(
                myPanel.getErrorViewStructure().getRootElement(), currentSessionId);
            updateIcon();
            myPanel.reload();
          }
        });
  }

  private void cleanupChildrenRecursively(
      @NotNull final Object fromElement, @NotNull UUID currentSessionId) {
    final ErrorViewStructure structure = myPanel.getErrorViewStructure();
    for (ErrorTreeElement element : structure.getChildElements(fromElement)) {
      if (element instanceof GroupingElement) {
        if (!currentSessionId.equals(element.getData())) {
          structure.removeElement(element);
        } else {
          cleanupChildrenRecursively(element, currentSessionId);
        }
      } else {
        if (!currentSessionId.equals(element.getData())) {
          structure.removeElement(element);
        }
      }
    }
  }

  public void addMessage(
      final int type,
      @NotNull final String[] text,
      @Nullable final String groupName,
      @Nullable final Navigatable navigatable,
      @Nullable final String exportTextPrefix,
      @Nullable final String rendererTextPrefix,
      @Nullable final UUID sessionId) {

    myViewUpdater.execute(
        () -> {
          final ErrorViewStructure structure = myPanel.getErrorViewStructure();
          final GroupingElement group = structure.lookupGroupingElement(groupName);
          if (group != null && sessionId != null && !sessionId.equals(group.getData())) {
            structure.removeElement(group);
          }
          if (navigatable != null) {
            myPanel.addMessage(
                type,
                text,
                groupName,
                navigatable,
                exportTextPrefix,
                rendererTextPrefix,
                sessionId);
          } else {
            myPanel.addMessage(type, text, null, -1, -1, sessionId);
          }
          updateIcon();
        });
  }

  private void updateIcon() {
    UIUtil.invokeLaterIfNeeded(
        () -> {
          if (!myProject.isDisposed()) {
            final ToolWindow tw =
                ToolWindowManager.getInstance(myProject)
                    .getToolWindow(BLAZE_PROBLEMS_TOOLWINDOW_ID);
            if (tw != null) {
              final boolean active = myPanel.getErrorViewStructure().hasMessages(ALL_MESSAGE_KINDS);
              tw.setIcon(active ? myActiveIcon : myPassiveIcon);
              if (active) {
                tw.show(null);
              }
            }
          }
        });
  }

  public void setProgress(String text, float fraction) {
    myPanel.setProgress(text, fraction);
  }

  public void setProgress(String text) {
    myPanel.setProgressText(text);
  }

  public void clearProgress() {
    myPanel.clearProgressData();
  }
}
