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
package com.google.idea.blaze.java.ui;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.ui.BlazeProblemsView;
import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.ProblemsView;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.UUID;

class BlazeIntelliJProblemsView implements BlazeProblemsView {
  private final Project project;

  private BlazeIntelliJProblemsView(Project project) {
    this.project = project;
  }

  @Override
  public void clearOldMessages(UUID sessionId) {
    ProblemsView.SERVICE.getInstance(project).clearOldMessages(null, sessionId);
  }

  @Override
  public void addMessage(IssueOutput issue, UUID sessionId) {
    VirtualFile virtualFile =
        issue.getFile() != null
            ? VfsUtil.findFileByIoFile(issue.getFile(), /* refresh */ true)
            : null;
    CompilerMessageCategory category =
        issue.getCategory() == IssueOutput.Category.ERROR
            ? CompilerMessageCategory.ERROR
            : CompilerMessageCategory.WARNING;
    CompilerMessageImpl message =
        new CompilerMessageImpl(
            project,
            category,
            issue.getMessage(),
            virtualFile,
            issue.getLine(),
            issue.getColumn(),
            issue.getNavigatable());
    ProblemsView.SERVICE.getInstance(project).addMessage(message, sessionId);
  }
}
