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
package com.google.idea.blaze.base.console;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import java.util.List;
import javax.annotation.Nullable;

/** Implementation for BlazeConsoleService */
public class BlazeConsoleServiceImpl implements BlazeConsoleService, ColoredTextAcceptor {
  private final Project project;
  private final BlazeConsoleView blazeConsoleView;
  private final AnsiEscapeDecoder ansiEscapeDecoder = new AnsiEscapeDecoder();

  BlazeConsoleServiceImpl(Project project) {
    this.project = project;
    blazeConsoleView = BlazeConsoleView.getInstance(project);
  }

  @Override
  public void print(String text, ConsoleViewContentType contentType) {
    Key<?> key =
        contentType == ConsoleViewContentType.ERROR_OUTPUT
            ? ProcessOutputTypes.STDERR
            : ProcessOutputTypes.STDOUT;
    ansiEscapeDecoder.escapeText(text, key, this);
  }

  @Override
  public void printHyperlink(String hyperlinkText, @Nullable HyperlinkInfo hyperlinkInfo) {
    blazeConsoleView.printHyperlink(hyperlinkText, hyperlinkInfo);
  }

  @Override
  public void coloredTextAvailable(String escapedText, @SuppressWarnings("rawtypes") Key key) {
    ConsoleViewContentType contentType = ConsoleViewContentType.getConsoleViewType(key);
    blazeConsoleView.print(escapedText, contentType);
  }

  @Override
  public void clear() {
    blazeConsoleView.clear();
  }

  @Override
  public void setCustomFilters(List<Filter> filters) {
    blazeConsoleView.setCustomFilters(filters);
  }

  @Override
  public void setStopHandler(@Nullable Runnable runnable) {
    blazeConsoleView.setStopHandler(runnable);
  }

  @Override
  public void activateConsoleWindow() {
    ToolWindow toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(BlazeConsoleToolWindowFactory.ID);
    if (toolWindow != null) {
      toolWindow.activate(/* runnable= */ null, /* autoFocusContents= */ false);
    }
  }
}
