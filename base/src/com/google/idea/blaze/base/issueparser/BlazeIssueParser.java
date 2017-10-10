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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parses blaze output for compile errors. */
public class BlazeIssueParser {

  /** Result from parsing the current line */
  public static class ParseResult {

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

  /** Used by BlazeIssueParser. Generally implemented by subclassing SingleLineParser */
  public interface Parser {
    ParseResult parse(String currentLine, List<String> previousLines);
  }

  /** Base for a Parser that consumes a single contextless line at a time, matched via regex */
  public abstract static class SingleLineParser implements Parser {
    Pattern pattern;

    public SingleLineParser(String regex) {
      pattern = Pattern.compile(regex);
    }

    @Override
    public ParseResult parse(String currentLine, List<String> multilineMatchResult) {
      checkState(
          multilineMatchResult.isEmpty(), "SingleLineParser recieved multiple lines of input");
      return parse(currentLine);
    }

    ParseResult parse(String line) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        return ParseResult.output(createIssue(matcher));
      }
      return ParseResult.noResult();
    }

    @Nullable
    protected abstract IssueOutput createIssue(Matcher matcher);
  }

  public static File fileFromAbsolutePath(String absolutePath) {
    return new File(absolutePath);
  }

  @Nullable
  public static File fileFromRelativePath(WorkspaceRoot workspaceRoot, String relativePath) {
    try {
      final WorkspacePath workspacePath = new WorkspacePath(relativePath);
      return workspaceRoot.fileForPath(workspacePath);
    } catch (IllegalArgumentException e) {
      // Ignore -- malformed error message
      return null;
    }
  }

  /** Returns the file referenced by the target */
  @Nullable
  private static File fileFromTarget(WorkspaceRoot workspaceRoot, String targetString) {
    Label label = Label.createIfValid(targetString);
    if (label == null || label.isExternal()) {
      return null;
    }
    try {
      final WorkspacePath combined =
          new WorkspacePath(label.blazePackage(), label.targetName().toString());
      return workspaceRoot.fileForPath(combined);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Falls back to returning -1 if no integer can be parsed. */
  public static int parseOptionalInt(@Nullable String intString) {
    if (intString == null) {
      return -1;
    }
    try {
      return Integer.parseInt(intString);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  static class CompileParser extends SingleLineParser {
    private final WorkspaceRoot workspaceRoot;

    CompileParser(WorkspaceRoot workspaceRoot) {
      super("^([^/].*?):([0-9]+):(?:([0-9]+):)? (fatal error|error|warning): (.*)$");
      this.workspaceRoot = workspaceRoot;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final File file = fileFromRelativePath(workspaceRoot, matcher.group(1));
      IssueOutput.Category type =
          matcher.group(4).equals("warning")
              ? IssueOutput.Category.WARNING
              : IssueOutput.Category.ERROR;
      return IssueOutput.issue(type, matcher.group(5))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .build();
    }
  }

  static class TracebackParser implements Parser {
    private static final Pattern PATTERN =
        Pattern.compile(
            "(ERROR): (.*?):([0-9]+):([0-9]+): (Traceback \\(most recent call last\\):)");

    @Override
    public ParseResult parse(String currentLine, List<String> previousLines) {
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
      super("^ERROR: (/.*?BUILD):([0-9]+):([0-9]+): (.*)$");
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = fileFromAbsolutePath(matcher.group(1));
      return IssueOutput.error(matcher.group(4))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .build();
    }
  }

  static class SkylarkErrorParser extends SingleLineParser {
    SkylarkErrorParser() {
      super("^ERROR: (/.*?\\.bzl):([0-9]+):([0-9]+): (.*)$");
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = fileFromAbsolutePath(matcher.group(1));
      return IssueOutput.error(matcher.group(4))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .build();
    }
  }

  static class LinelessBuildParser extends SingleLineParser {
    LinelessBuildParser() {
      super("^ERROR: (.*?):char offsets [0-9]+--[0-9]+: (.*)$");
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      return IssueOutput.error(matcher.group(2)).inFile(new File(matcher.group(1))).build();
    }
  }

  static class FileNotFoundBuildParser extends SingleLineParser {
    private final WorkspaceRoot workspaceRoot;

    FileNotFoundBuildParser(WorkspaceRoot workspaceRoot) {
      super("^ERROR: .*? Unable to load file '(.*?)': (.*)$");
      this.workspaceRoot = workspaceRoot;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = fileFromTarget(workspaceRoot, matcher.group(1));
      return IssueOutput.error(matcher.group(2)).inFile(file).build();
    }
  }

  static class ProjectViewLabelParser extends SingleLineParser {

    @Nullable private final ProjectViewSet projectViewSet;

    ProjectViewLabelParser(@Nullable ProjectViewSet projectViewSet) {
      super("no such target '(.*)': target .*? not declared in package .*? defined by");
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = null;
      if (projectViewSet != null) {
        String targetString = matcher.group(1);
        final TargetExpression target = TargetExpression.fromStringSafe(targetString);
        if (target != null) {
          file =
              projectViewFileWithSection(
                  projectViewSet,
                  TargetSection.KEY,
                  targetSection -> targetSection.items().contains(target));
        }
      }

      return IssueOutput.error(matcher.group(0)).inFile(file).build();
    }
  }

  static class InvalidTargetProjectViewPackageParser extends SingleLineParser {
    private final ProjectViewSet projectViewSet;

    InvalidTargetProjectViewPackageParser(ProjectViewSet projectViewSet, String regex) {
      super(regex);
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final String packageString = matcher.group(1);
      File file =
          projectViewFileWithSection(
              projectViewSet,
              TargetSection.KEY,
              targetSection -> {
                for (TargetExpression targetExpression : targetSection.items()) {
                  if (targetExpression.toString().contains(packageString)) {
                    return true;
                  }
                }
                return false;
              });

      return IssueOutput.error(matcher.group(0)).inFile(file).build();
    }
  }

  @Nullable
  private static <T, SectionType extends Section<T>> File projectViewFileWithSection(
      ProjectViewSet projectViewSet,
      SectionKey<T, SectionType> key,
      Predicate<SectionType> predicate) {
    for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
      ImmutableList<SectionType> sections = projectViewFile.projectView.getSectionsOfType(key);
      for (SectionType section : sections) {
        if (predicate.test(section)) {
          return projectViewFile.projectViewFile;
        }
      }
    }
    return null;
  }

  private ImmutableList<Parser> parsers;
  /**
   * The parser that requested more lines of input during the last call to {@link
   * #parseIssue(String)}.
   */
  @Nullable private Parser multilineMatchingParser;

  private List<String> multilineMatchResult = new ArrayList<>();

  public BlazeIssueParser(ImmutableList<Parser> parsers) {
    this.parsers = parsers;
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
