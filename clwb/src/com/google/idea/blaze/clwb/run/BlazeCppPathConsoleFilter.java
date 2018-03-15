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
package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.run.filter.FileResolver;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Resolves file paths in C++ console output. */
public class BlazeCppPathConsoleFilter implements Filter, DumbAware {

  static class Provider implements ConsoleFilterProvider {
    @Override
    public Filter[] getDefaultFilters(Project project) {
      return Blaze.isBlazeProject(project)
          ? new Filter[] {new BlazeCppPathConsoleFilter(project)}
          : new Filter[0];
    }
  }

  static final Pattern PATTERN = Pattern.compile("^(.*):([0-9]+)(: Failure)?$");

  private final Project project;

  private BlazeCppPathConsoleFilter(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = PATTERN.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    String filePath = matcher.group(1);
    if (filePath == null) {
      return null;
    }
    VirtualFile file = resolveFile(filePath);
    if (file == null) {
      return null;
    }
    int lineNumber = parseLineNumber(matcher.group(2));
    OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(project, file, lineNumber - 1);

    int offset = entireLength - line.length();
    return new Result(matcher.start() + offset, matcher.end() + offset, hyperlink);
  }

  /** defaults to -1 if no line number can be parsed. */
  private static int parseLineNumber(@Nullable String string) {
    try {
      return string != null ? Integer.parseInt(string) : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @Nullable
  private VirtualFile resolveFile(String relativePath) {
    try {
      return FileResolver.resolve(project, relativePath);
    } catch (IndexNotReadyException e) {
      // Filter was called in dumb mode.
      // Not a problem since the console will re-run the filters after exiting dumb mode.
      return null;
    }
  }
}
