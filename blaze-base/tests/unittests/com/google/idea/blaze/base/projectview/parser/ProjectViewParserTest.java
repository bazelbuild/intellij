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
package com.google.idea.blaze.base.projectview.parser;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class ProjectViewParserTest extends BlazeTestCase {
  private ProjectViewParser projectViewParser;
  private BlazeContext context;
  private ErrorCollector errorCollector;
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));
  private MockProjectViewStorageManager projectViewStorageManager;

  static class MockProjectViewStorageManager extends ProjectViewStorageManager {
    Map<String, String> projectViewFiles = Maps.newHashMap();
    @Nullable
    @Override
    public String loadProjectView(@NotNull File projectViewFile) throws IOException {
      return projectViewFiles.get(projectViewFile.getPath());
    }

    @Override
    public void writeProjectView(@NotNull String projectViewText, @NotNull File projectViewFile) throws IOException {
      // no-op
    }

    void add(String name, String... text) {
      projectViewFiles.put(name, Joiner.on('\n').join(text));
    }
  }

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    context = new BlazeContext();
    errorCollector = new ErrorCollector();
    context.addOutputSink(IssueOutput.class, errorCollector);
    projectViewParser = new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewStorageManager = new MockProjectViewStorageManager();
    applicationServices.register(ProjectViewStorageManager.class, projectViewStorageManager);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
  }

  @Test
  public void testDirectoriesAndTargets() throws Exception {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  java/com/google",
                                  "  java/com/google/android",
                                  "  -java/com/google/android/notme",
                                  "",
                                  "targets:",
                                  "  //java/com/google:all",
                                  "  //java/com/google/...:all",
                                  "  -//java/com/google:thistarget"
    );
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    assertThat(projectViewFile).isNotNull();
    assertThat(projectViewFile.projectViewFile).isEqualTo(new File(".blazeproject"));
    assertThat(projectViewSet.getProjectViewFiles()).containsExactly(projectViewFile);

    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionOfType(DirectorySection.KEY).items())
      .containsExactly(
        new DirectoryEntry(new WorkspacePath("java/com/google"), true),
        new DirectoryEntry(new WorkspacePath("java/com/google/android"), true),
        new DirectoryEntry(new WorkspacePath("java/com/google/android/notme"), false)
      );
    assertThat(projectView.getSectionOfType(TargetSection.KEY).items())
      .containsExactly(
        TargetExpression.fromString("//java/com/google:all"),
        TargetExpression.fromString("//java/com/google/...:all"),
        TargetExpression.fromString("-//java/com/google:thistarget")
      );
  }

  @Test
  public void testRootDirectory() throws Exception {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  .",
                                  "  -java/com/google/android/notme",
                                  "",
                                  "targets:",
                                  "  //java/com/google:all"
    );
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    assertThat(projectViewFile).isNotNull();
    assertThat(projectViewFile.projectViewFile).isEqualTo(new File(".blazeproject"));
    assertThat(projectViewSet.getProjectViewFiles()).containsExactly(projectViewFile);

    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionOfType(DirectorySection.KEY).items())
      .containsExactly(
        new DirectoryEntry(new WorkspacePath(""), true),
        new DirectoryEntry(new WorkspacePath("java/com/google/android/notme"), false)
      );
    assertThat(projectView.getSectionOfType(TargetSection.KEY).items())
      .containsExactly(
        TargetExpression.fromString("//java/com/google:all")
      );

    String text = ProjectViewParser.projectViewToString(projectView);
    assertThat(text).isEqualTo(
      Joiner.on('\n').join(
        "directories:",
        "  .",
        "  -java/com/google/android/notme",
        "",
        "targets:",
        "  //java/com/google:all",
        ""
      )
    );
  }

  @Test
  public void testPrint() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/one")))
             .add(DirectoryEntry.exclude(new WorkspacePath("java/com/google/two"))))
      .put(ListSection.builder(TargetSection.KEY)
             .add(TargetExpression.fromString("//java/com/google:one"))
             .add(TargetExpression.fromString("//java/com/google:two")))
      .put(ScalarSection.builder(ImportSection.KEY)
             .set(new WorkspacePath("some/file.blazeproject")))
      .build();
    String text = ProjectViewParser.projectViewToString(projectView);
    assertThat(text).isEqualTo(
      Joiner.on('\n').join(
        "import some/file.blazeproject",
        "",
        "directories:",
        "  java/com/google/one",
        "  -java/com/google/two",
        "",
        "targets:",
        "  //java/com/google:one",
        "  //java/com/google:two",
        ""
      ));
  }

  @Test
  public void testImport() {
    projectViewStorageManager.add("/parent.blazeproject",
                                  "directories:",
                                  "  parent",
                                  "");
    projectViewStorageManager.add(".blazeproject",
                                  "import parent.blazeproject",
                                  "directories:",
                                  "  child",
                                  "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    assertThat(projectViewSet.getProjectViewFiles()).hasSize(2);
    Collection<DirectoryEntry> entries = projectViewSet.listItems(DirectorySection.KEY);
    assertThat(entries).containsExactly(
      new DirectoryEntry(new WorkspacePath("parent"), true),
      new DirectoryEntry(new WorkspacePath("child"), true)
    );
  }

  @Test
  public void testMinimumIndentRequired() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  java/com/google",
                                  "java/com/google2",
                                  "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'java/com/google2'");
  }

  @Test
  public void testIncorrectIndentationResultsInIssue() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  java/com/google",
                                  " java/com/google2",
                                  "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Invalid indentation. Project view files are indented with 2 spaces.");
  }

  @Test
  public void testCanParseWithMissingCarriageReturnAtEndOfSection() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    ProjectView projectView = projectViewParser.getResult().getTopLevelProjectViewFile().projectView;
    assertThat(projectView.getSectionOfType(DirectorySection.KEY).items())
      .containsExactly(new DirectoryEntry(new WorkspacePath("java/com/google"), true));
  }

  @Test
  public void testWhitespaceIsIgnoredBetweenSections() {
    projectViewStorageManager.add(".blazeproject",
                                  "",
                                  "directories:",
                                  "  java/com/google",
                                  "",
                                  "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    ProjectView projectView = projectViewParser.getResult().getTopLevelProjectViewFile().projectView;
    assertThat(projectView.getSectionOfType(DirectorySection.KEY).items())
      .containsExactly(new DirectoryEntry(new WorkspacePath("java/com/google"), true));
  }

  @Test
  public void testImportMissingFileResultsInIssue() {
    projectViewStorageManager.add(".blazeproject",
                                  "import parent.blazeproject");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not load project view file: '/parent.blazeproject'");
  }

  @Test
  public void testDuplicateSectionsResultsInIssue() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "  java/com/google",
                                  "directories:",
                                  "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Duplicate attribute: 'directories'");
  }

  @Test
  public void testMissingSectionResultsInIssue() {
    projectViewStorageManager.add(".blazeproject",
                                  "nosuchsection:",
                                  "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'nosuchsection:'");
  }

  @Test
  public void testMissingColonResultInIssue() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories",
                                  "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'directories'");
  }

  @Test
  public void testEmptySectionYieldsError() {
    projectViewStorageManager.add(".blazeproject",
                                  "directories:",
                                  "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Empty section: 'directories'");
  }

  @Test
  public void testCommentsAreSkipped() throws Exception {
    projectViewStorageManager.add(".blazeproject",
                                  "# comment",
                                  "directories:",
                                  "# another comment",
                                  "  java/com/google",
                                  "  # comment",
                                  "  java/com/google/android",
                                  ""
    );
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionOfType(DirectorySection.KEY).items())
      .containsExactly(
        new DirectoryEntry(new WorkspacePath("java/com/google"), true),
        new DirectoryEntry(new WorkspacePath("java/com/google/android"), true)
      );
  }
}
