/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run.filter;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.filter.FileResolver;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parses traceback links in python test results */
public class BlazePyTracebackFilter implements Filter {

  /** Provider for traceback filter */
  public static class BlazePyTracebackFilterProvider implements ConsoleFilterProvider {
    @Override
    public Filter[] getDefaultFilters(Project project) {
      return Blaze.isBlazeProject(project)
          ? new Filter[] {new BlazePyTracebackFilter(project)}
          : new Filter[0];
    }
  }

  // format: File "/path/to/file", line 123, in function_name
  private static final Pattern TRACEBACK_FILE_LINE =
      Pattern.compile("File \"(?<filepath>.*?)\", (?<link>line (?<lineno>[0-9]+)), in (.*?)");

  // format: /path/to/file:123: Arbitrary message here
  private static final Pattern WARNING_LINE =
      Pattern.compile("(?<link>(?<filepath>.+?):(?<lineno>[0-9]+):) .*");

  // Each of these patterns has 3 named groups:
  // - filepath: the path of the file to link to
  // - lineno: the line number in that file to link to
  // - link: the part of the pattern that should be the link
  private static final ImmutableList<Pattern> patterns =
      ImmutableList.of(TRACEBACK_FILE_LINE, WARNING_LINE);

  private final Project project;

  private BlazePyTracebackFilter(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = null;
    for (Pattern p : patterns) {
      matcher = p.matcher(line);
      if (matcher.find()) {
        break;
      }
      matcher = null;
    }
    if (matcher == null) {
      return null;
    }
    String filePath = matcher.group("filepath");
    if (filePath == null) {
      return null;
    }
    if (filePath.startsWith("//")) {
      filePath = filePath.substring(2);
    }
    VirtualFile file = FileResolver.resolveToVirtualFile(project, filePath);
    if (file == null) {
      return null;
    }

    int lineNumber = parseLineNumber(matcher.group("lineno"));
    OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(project, file, lineNumber - 1);

    int startIx = matcher.start("link");
    int endIx = matcher.end("link");
    int offset = entireLength - line.length();
    return new Result(startIx + offset, endIx + offset, hyperlink);
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
