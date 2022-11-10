package com.google.idea.blaze.gazelle;

import static com.google.idea.blaze.base.issueparser.BlazeIssueParser.*;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.run.filter.FileResolver;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.IssueOutput.Category;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.regex.Matcher;
import org.jetbrains.annotations.Nullable;

public class GazelleIssueParsers {

  public static ImmutableList<BlazeIssueParser.Parser> allGazelleIssueParsers(Project project) {
    // We eventually may want to introduce per-language gazelle error parsing,
    // so we expose this function to conveniently gather all of them.
    return ImmutableList.of(new GazelleIssueParsers.GenericGazelleIssueParser(project));
  }

  private static final String GAZELLE_ISSUE_REGEX =
      // Prefix for gazelle errors. We match the beginning to get rid of the control characters.
      "^.*gazelle: " +
          // File
          "(.*?): " +
          // Message
          "(.*)$";

  private static class GenericGazelleIssueParser extends BlazeIssueParser.SingleLineParser {

    Project project;

    GenericGazelleIssueParser(Project project) {
      super(GAZELLE_ISSUE_REGEX);
      this.project = project;
    }

    @Nullable
    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final File file = FileResolver.resolveToFile(project, matcher.group(1));
      return IssueOutput.issue(Category.ERROR, matcher.group(2))
          .inFile(file)
          .consoleHyperlinkRange(
              union(fileHighlightRange(matcher, 1), matchedTextRange(matcher, 1, 1)))
          .build();
    }
  }
}
