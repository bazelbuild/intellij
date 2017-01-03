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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/**
 * Forwards output to PrintOutputs, colored by whether or not an issue is found per-line.
 *
 * <p>Also creates IssueOutput if issues are found.
 */
public class IssueOutputLineProcessor implements LineProcessingOutputStream.LineProcessor {

  private final BlazeContext context;

  private final BlazeIssueParser blazeIssueParser;

  public IssueOutputLineProcessor(
      @Nullable Project project, BlazeContext context, WorkspaceRoot workspaceRoot) {
    this.context = context;
    ProjectViewSet projectViewSet =
        project != null ? ProjectViewManager.getInstance(project).getProjectViewSet() : null;

    ImmutableList<BlazeIssueParser.Parser> parsers =
        ImmutableList.of(
            new BlazeIssueParser.CompileParser(workspaceRoot),
            new BlazeIssueParser.TracebackParser(),
            new BlazeIssueParser.BuildParser(),
            new BlazeIssueParser.LinelessBuildParser(),
            new BlazeIssueParser.ProjectViewLabelParser(projectViewSet),
            new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                projectViewSet, "no such package '(.*)': BUILD file not found on package path"),
            new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                projectViewSet, "no targets found beneath '(.*)'"),
            new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                projectViewSet, "ERROR: invalid target format '(.*)'"),
            new BlazeIssueParser.FileNotFoundBuildParser(workspaceRoot));
    this.blazeIssueParser = new BlazeIssueParser(parsers);
  }

  public IssueOutputLineProcessor(
      BlazeContext context, ImmutableList<BlazeIssueParser.Parser> parsers) {
    this.context = context;
    this.blazeIssueParser = new BlazeIssueParser(parsers);
  }

  @Override
  public boolean processLine(String line) {
    IssueOutput issue = blazeIssueParser.parseIssue(line);
    if (issue != null) {
      if (issue.getCategory() == IssueOutput.Category.ERROR) {
        context.setHasError();
      }
      context.output(issue);
    }

    OutputType outputType = issue == null ? OutputType.NORMAL : OutputType.ERROR;

    context.output(new PrintOutput(line, outputType));
    return true;
  }
}
