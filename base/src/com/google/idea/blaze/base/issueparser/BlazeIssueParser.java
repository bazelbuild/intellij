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
package com.google.idea.blaze.base.issueparser;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Parses blaze output for compile errors. */
public class BlazeIssueParser {

  private static class ParseResult {

    public static final ParseResult NEEDS_MORE_INPUT = new ParseResult(true, null);

    public static final ParseResult NO_RESULT = new ParseResult(false, null);

    private boolean needsMoreInput;
    @Nullable private IssueOutput output;

    private ParseResult(boolean needsMoreInput, IssueOutput output) {
      this.needsMoreInput = needsMoreInput;
      this.output = output;
    }

    public static ParseResult needsMoreInput() {
      return NEEDS_MORE_INPUT;
    }

    public static ParseResult output(IssueOutput output) {
      return new ParseResult(false, output);
    }

    public static ParseResult noResult() {
      return NO_RESULT;
    }
  }

  interface Parser {
    @NotNull
    ParseResult parse(@NotNull String currentLine, @NotNull List<String> previousLines);
  }

  abstract static class SingleLineParser implements Parser {
    @NotNull Pattern pattern;

    SingleLineParser(@NotNull String regex) {
      pattern = Pattern.compile(regex);
    }

    @Override
    public ParseResult parse(
        @NotNull String currentLine, @NotNull List<String> multilineMatchResult) {
      checkState(
          multilineMatchResult.isEmpty(), "SingleLineParser recieved multiple lines of input");
      return parse(currentLine);
    }

    ParseResult parse(@NotNull String line) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        return ParseResult.output(createIssue(matcher));
      }
      return ParseResult.noResult();
    }

    @Nullable
    protected abstract IssueOutput createIssue(@NotNull Matcher matcher);
  }

  static class CompileParser extends SingleLineParser {
    @NotNull private final WorkspaceRoot workspaceRoot;

    public CompileParser(@NotNull WorkspaceRoot workspaceRoot) {
      super("(.*?):([0-9]+):([0-9]+:)? (error|warning): (.*)");
      this.workspaceRoot = workspaceRoot;
    }

    @Override
    protected IssueOutput createIssue(@NotNull Matcher matcher) {
      final File file;
      try {
        String fileName = matcher.group(1);
        final WorkspacePath workspacePath;
        if (fileName.startsWith("//depot/google3/")) {
          workspacePath = new WorkspacePath(fileName.substring("//depot/google3/".length()));
        } else if (fileName.startsWith("/")) {
          workspacePath = workspaceRoot.workspacePathFor(new File(fileName));
        } else {
          workspacePath = new WorkspacePath(fileName);
        }
        file = workspaceRoot.fileForPath(workspacePath);
      } catch (IllegalArgumentException e) {
        // Ignore -- malformed error message
        return null;
      }

      IssueOutput.Category type =
          matcher.group(4).equals("error")
              ? IssueOutput.Category.ERROR
              : IssueOutput.Category.WARNING;
      return IssueOutput.issue(type, matcher.group(5))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(4)))
          .build();
    }
  }

  static class TracebackParser implements Parser {
    private static final Pattern PATTERN =
        Pattern.compile(
            "(ERROR): (.*?):([0-9]+):([0-9]+): (Traceback \\(most recent call last\\):)");

    @NotNull
    @Override
    public ParseResult parse(@NotNull String currentLine, @NotNull List<String> previousLines) {
      if (previousLines.isEmpty()) {
        if (PATTERN.matcher(currentLine).find()) {
          return ParseResult.needsMoreInput();
        } else {
          return ParseResult.noResult();
        }
      }

      if (currentLine.startsWith("\t")) {
        return ParseResult.needsMoreInput();
      } else {
        Matcher matcher = PATTERN.matcher(previousLines.get(0));
        checkState(
            matcher.find(), "Found a match in the first line previously, but now it isn't there.");
        StringBuilder message = new StringBuilder(matcher.group(5));
        for (int i = 1; i < previousLines.size(); ++i) {
          message.append(System.lineSeparator()).append(previousLines.get(i));
        }
        message.append(System.lineSeparator()).append(currentLine);
        return ParseResult.output(
            IssueOutput.error(message.toString())
                .inFile(new File(matcher.group(2)))
                .onLine(Integer.parseInt(matcher.group(3)))
                .inColumn(parseOptionalInt(matcher.group(4)))
                .build());
      }
    }
  }

  static class BuildParser extends SingleLineParser {
    BuildParser() {
      super("(ERROR): (.*?):([0-9]+):([0-9]+): (.*)");
    }

    @Override
    protected IssueOutput createIssue(@NotNull Matcher matcher) {
      return IssueOutput.error(matcher.group(5))
          .inFile(new File(matcher.group(2)))
          .onLine(Integer.parseInt(matcher.group(3)))
          .inColumn(parseOptionalInt(matcher.group(4)))
          .build();
    }
  }

  /** Falls back to returning -1 if no integer can be parsed. */
  private static int parseOptionalInt(String intString) {
    try {
      return Integer.parseInt(intString);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  static class LinelessBuildParser extends SingleLineParser {
    LinelessBuildParser() {
      super("(ERROR): (.*?):char offsets [0-9]+--[0-9]+: (.*)");
    }

    @Override
    protected IssueOutput createIssue(@NotNull Matcher matcher) {
      return IssueOutput.error(matcher.group(3)).inFile(new File(matcher.group(2))).build();
    }
  }

  static class ProjectViewLabelParser extends SingleLineParser {

    @Nullable private final ProjectViewSet projectViewSet;

    ProjectViewLabelParser(@Nullable ProjectViewSet projectViewSet) {
      super("no such target '(.*)': target .*? not declared in package .*? defined by");
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(@NotNull Matcher matcher) {
      File file = null;
      if (projectViewSet != null) {
        String targetString = matcher.group(1);
        final TargetExpression targetExpression = TargetExpression.fromString(targetString);
        file =
            projectViewFileWithSection(
                projectViewSet,
                TargetSection.KEY,
                new Predicate<ListSection<TargetExpression>>() {
                  @Override
                  public boolean apply(@NotNull ListSection<TargetExpression> targetSection) {
                    return targetSection.items().contains(targetExpression);
                  }
                });
      }

      return IssueOutput.error(matcher.group(0)).inFile(file).build();
    }
  }

  static class InvalidTargetProjectViewPackageParser extends SingleLineParser {
    @Nullable private final ProjectViewSet projectViewSet;

    InvalidTargetProjectViewPackageParser(@Nullable ProjectViewSet projectViewSet, String regex) {
      super(regex);
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(@NotNull Matcher matcher) {
      File file = null;
      if (projectViewSet != null) {
        final String packageString = matcher.group(1);
        file =
            projectViewFileWithSection(
                projectViewSet,
                TargetSection.KEY,
                targetSection -> {
                  for (TargetExpression targetExpression : targetSection.items()) {
                    if (targetExpression.toString().startsWith("//" + packageString + ":")) {
                      return true;
                    }
                  }
                  return false;
                });
      }

      return IssueOutput.error(matcher.group(0)).inFile(file).build();
    }
  }

  @Nullable
  private static <T, SectionType extends Section<T>> File projectViewFileWithSection(
      @NotNull ProjectViewSet projectViewSet,
      @NotNull SectionKey<T, SectionType> key,
      @NotNull Predicate<SectionType> predicate) {
    for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
      ImmutableList<SectionType> sections = projectViewFile.projectView.getSectionsOfType(key);
      for (SectionType section : sections) {
        if (predicate.apply(section)) {
          return projectViewFile.projectViewFile;
        }
      }
    }
    return null;
  }

  @NotNull private List<Parser> parsers = Lists.newArrayList();
  /**
   * The parser that requested more lines of input during the last call to {@link
   * #parseIssue(String)}.
   */
  @Nullable private Parser multilineMatchingParser;

  @NotNull private List<String> multilineMatchResult = new ArrayList<>();

  public BlazeIssueParser(@Nullable Project project, @NotNull WorkspaceRoot workspaceRoot) {

    ProjectViewSet projectViewSet =
        project != null ? ProjectViewManager.getInstance(project).getProjectViewSet() : null;

    parsers.add(new CompileParser(workspaceRoot));
    parsers.add(new TracebackParser());
    parsers.add(new BuildParser());
    parsers.add(new LinelessBuildParser());
    parsers.add(new ProjectViewLabelParser(projectViewSet));
    parsers.add(
        new InvalidTargetProjectViewPackageParser(
            projectViewSet, "no such package '(.*)': BUILD file not found on package path"));
    parsers.add(
        new InvalidTargetProjectViewPackageParser(
            projectViewSet, "no targets found beneath '(.*)'"));
    parsers.add(
        new InvalidTargetProjectViewPackageParser(
            projectViewSet, "ERROR: invalid target format '(.*)'"));
  }

  @Nullable
  public IssueOutput parseIssue(String line) {

    List<Parser> parsers = this.parsers;
    if (multilineMatchingParser != null) {
      parsers = Lists.newArrayList(multilineMatchingParser);
    }

    for (Parser parser : parsers) {
      ParseResult issue = parser.parse(line, multilineMatchResult);
      if (issue.needsMoreInput) {
        multilineMatchingParser = parser;
        multilineMatchResult.add(line);
        return null;
      } else {
        multilineMatchingParser = null;
        multilineMatchResult = new ArrayList<>();
      }

      if (issue.output != null) {
        return issue.output;
      }
    }

    return null;
  }
}
