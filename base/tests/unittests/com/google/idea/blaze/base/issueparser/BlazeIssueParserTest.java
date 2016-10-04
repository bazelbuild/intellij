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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeIssueParser}. */
@RunWith(JUnit4.class)
public class BlazeIssueParserTest extends BlazeTestCase {

  private ProjectViewManager projectViewManager;
  private WorkspaceRoot workspaceRoot;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(ExperimentService.class, new MockExperimentService());

    projectViewManager = mock(ProjectViewManager.class);
    projectServices.register(ProjectViewManager.class, projectViewManager);

    workspaceRoot = new WorkspaceRoot(new File("/root"));
  }

  @Test
  public void testParseTargetError() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "ERROR: invalid target format "
                + "'//javatests/com/google/devtools/aswb/testapps/aswbtestlib/...:alls': "
                + "invalid package name "
                + "'javatests/com/google/devtools/aswb/testapps/aswbtestlib/...': "
                + "package name component contains only '.' characters.");
    assertNotNull(issue);
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testParseCompileError() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "java/com/google/android/samples/helloroot/math/DivideMath.java:17: error: "
                + "non-static variable this cannot be referenced from a static context");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath())
        .isEqualTo("/root/java/com/google/android/samples/helloroot/math/DivideMath.java");
    assertThat(issue.getLine()).isEqualTo(17);
    assertThat(issue.getMessage())
        .isEqualTo("non-static variable this cannot be referenced from a static context");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testParseCompileErrorWithColumn() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "java/com/google/devtools/aswb/pluginrepo/googleplex/PluginsEndpoint.java:33:26: "
                + "error: '|' is not preceded with whitespace.");
    assertNotNull(issue);
    assertThat(issue.getLine()).isEqualTo(33);
    assertThat(issue.getMessage()).isEqualTo("'|' is not preceded with whitespace.");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testParseCompileErrorWithAbsolutePath() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "/root/java/com/google/android/samples/helloroot/math/DivideMath.java:17: error: "
                + "non-static variable this cannot be referenced from a static context");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath())
        .isEqualTo("/root/java/com/google/android/samples/helloroot/math/DivideMath.java");
  }

  @Test
  public void testParseCompileErrorWithDepotPath() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "//depot/google3/package_path/DivideMath.java:17: error: "
                + "non-static variable this cannot be referenced from a static context");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo("/root/package_path/DivideMath.java");
  }

  @Test
  public void testParseBuildError() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "ERROR: /path/to/root/javatests/package_path/BUILD:42:12: "
                + "Target '//java/package_path:helloroot_visibility' failed");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo("/path/to/root/javatests/package_path/BUILD");
    assertThat(issue.getLine()).isEqualTo(42);
    assertThat(issue.getMessage())
        .isEqualTo("Target '//java/package_path:helloroot_visibility' failed");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testParseLinelessBuildError() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "ERROR: /path/to/root/java/package_path/BUILD:char offsets 1222--1229: "
                + "name 'grubber' is not defined");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo("/path/to/root/java/package_path/BUILD");
    assertThat(issue.getMessage()).isEqualTo("name 'grubber' is not defined");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testLabelProjectViewParser() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                new File(".blazeproject"),
                ProjectView.builder()
                    .add(
                        ListSection.builder(TargetSection.KEY)
                            .add(TargetExpression.fromString("//package/path:hello4")))
                    .build())
            .build();
    when(projectViewManager.getProjectViewSet()).thenReturn(projectViewSet);

    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "no such target '//package/path:hello4': "
                + "target 'hello4' not declared in package 'package/path' "
                + "defined by /path/to/root/package/path/BUILD");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo(".blazeproject");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testPackageProjectViewParser() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                new File(".blazeproject"),
                ProjectView.builder()
                    .add(
                        ListSection.builder(TargetSection.KEY)
                            .add(TargetExpression.fromString("//package/path:hello4")))
                    .build())
            .build();
    when(projectViewManager.getProjectViewSet()).thenReturn(projectViewSet);

    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "no such package 'package/path': BUILD file not found on package path");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo(".blazeproject");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testDeletedBUILDFileButLeftPackageInLocalTargets() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                new File(".blazeproject"),
                ProjectView.builder()
                    .add(
                        ListSection.builder(TargetSection.KEY)
                            .add(TargetExpression.fromString("//tests/com/google/a/b/c/d/baz:baz")))
                    .build())
            .build();
    when(projectViewManager.getProjectViewSet()).thenReturn(projectViewSet);

    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "Error:com.google.a.b.Exception exception in Bar: no targets found beneath "
                + "'tests/com/google/a/b/c/d/baz' Thrown during call: ...");
    assertNotNull(issue);
    assertNotNull(issue.getFile());
    assertThat(issue.getFile().getPath()).isEqualTo(".blazeproject");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
    assertThat(issue.getMessage())
        .isEqualTo("no targets found beneath 'tests/com/google/a/b/c/d/baz'");
  }

  @Test
  public void testMultilineTraceback() {
    String[] lines =
        new String[] {
          "ERROR: /home/plumpy/whatever:9:12: Traceback (most recent call last):",
          "\tFile \"/path/to/root/java/com/google/android/samples/helloroot/BUILD\", line 8",
          "\t\tpackage_group(name = BAD_FUNCTION(\"hellogoogle...\"), ...\"])",
          "\tFile \"/path/to/root/java/com/google/android/samples/helloroot/BUILD\", line 9, "
              + "in package_group",
          "\t\tBAD_FUNCTION",
          "name 'BAD_FUNCTION' is not defined."
        };

    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    for (int i = 0; i < lines.length - 1; ++i) {
      IssueOutput issue = blazeIssueParser.parseIssue(lines[i]);
      assertNull(issue);
    }

    IssueOutput issue = blazeIssueParser.parseIssue(lines[lines.length - 1]);
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo("/home/plumpy/whatever");
    assertThat(issue.getMessage().split("\n")).hasLength(lines.length);
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testLineAfterTracebackIsAlsoParsed() {
    String[] lines =
        new String[] {
          "ERROR: /home/plumpy/whatever:9:12: Traceback (most recent call last):",
          "\tFile \"/path/to/root/java/com/google/android/samples/helloroot/BUILD\", line 8",
          "\t\tpackage_group(name = BAD_FUNCTION(\"hellogoogle...\"), ...\"])",
          "\tFile \"/path/to/root/java/com/google/android/samples/helloroot/BUILD\", line 9, "
              + "in package_group",
          "\t\tBAD_FUNCTION",
          "name 'BAD_FUNCTION' is not defined."
        };

    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    for (int i = 0; i < lines.length; ++i) {
      blazeIssueParser.parseIssue(lines[i]);
    }

    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "ERROR: /home/plumpy/whatever:char offsets 1222--1229: name 'grubber' is not defined");
    assertNotNull(issue);
    assertThat(issue.getFile().getPath()).isEqualTo("/home/plumpy/whatever");
    assertThat(issue.getMessage()).isEqualTo("name 'grubber' is not defined");
    assertThat(issue.getCategory()).isEqualTo(IssueOutput.Category.ERROR);
  }

  @Test
  public void testMultipleIssues() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(project, workspaceRoot);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "ERROR: /home/plumpy/whatever:char offsets 1222--1229: name 'grubber' is not defined");
    assertNotNull(issue);
    issue =
        blazeIssueParser.parseIssue(
            "ERROR: /home/plumpy/whatever:char offsets 1222--1229: name 'grubber' is not defined");
    assertNotNull(issue);
    issue =
        blazeIssueParser.parseIssue(
            "ERROR: /home/plumpy/whatever:char offsets 1222--1229: name 'grubber' is not defined");
    assertNotNull(issue);
  }
}
