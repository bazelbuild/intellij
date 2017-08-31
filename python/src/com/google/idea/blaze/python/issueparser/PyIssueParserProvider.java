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
package com.google.idea.blaze.python.issueparser;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.SingleLineParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParserProvider;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Arrays;
import java.util.regex.Matcher;
import javax.annotation.Nullable;

/** Finds python-specific errors in blaze build output. */
public class PyIssueParserProvider implements BlazeIssueParserProvider {

  @Override
  public ImmutableList<Parser> getIssueParsers(Project project) {
    return ImmutableList.of(new PyTracebackIssueParser(project));
  }

  private static class PyTracebackIssueParser extends SingleLineParser {

    final Project project;

    PyTracebackIssueParser(Project project) {
      super("File \"(.*?)\", line ([0-9]+), in (.*)");
      this.project = project;
    }

    @Nullable
    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      String fileName = matcher.group(1);
      if (fileName == null) {
        return null;
      }
      return IssueOutput.error(matcher.group(0))
          .navigatable(openFileNavigatable(project, fileName, parseLineNumber(matcher.group(2))))
          .build();
    }

    private static Navigatable openFileNavigatable(Project project, String fileName, int line) {
      return new NavigatableAdapter() {
        @Override
        public void navigate(boolean requestFocus) {
          openFile(project, fileName, line, requestFocus);
        }
      };
    }

    private static void openFile(Project project, String fileName, int line, boolean requestFocus) {
      PsiFile file = findFile(project, fileName);
      if (file == null) {
        return;
      }
      new OpenFileDescriptor(project, file.getViewProvider().getVirtualFile(), line - 1, -1)
          .navigate(requestFocus);
    }

    @Nullable
    private static PsiFile findFile(Project project, String fileName) {
      return Arrays.stream(
              FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project)))
          .findFirst()
          .orElse(null);
    }

    /** defaults to -1 if no line number can be parsed. */
    private static int parseLineNumber(@Nullable String string) {
      try {
        return string != null ? Integer.parseInt(string) : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }
  }
}
