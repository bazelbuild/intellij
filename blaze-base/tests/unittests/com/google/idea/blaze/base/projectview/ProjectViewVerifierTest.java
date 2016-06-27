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
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.io.MockWorkspaceScanner;
import com.google.idea.blaze.base.io.WorkspaceScanner;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

/**
 * Tests for ProjectViewVerifier
 */
public class ProjectViewVerifierTest extends BlazeTestCase {

  private String FAKE_ROOT = "/root";
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_ROOT));
  private MockWorkspaceScanner workspaceScanner;
  private ErrorCollector errorCollector = new ErrorCollector();
  private BlazeContext context;
  private WorkspaceLanguageSettings workspaceLanguageSettings = new WorkspaceLanguageSettings(
    WorkspaceType.JAVA,
    ImmutableSet.of( LanguageClass.JAVA)
  );

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);

    workspaceScanner = new MockWorkspaceScanner();
    applicationServices.register(WorkspaceScanner.class, workspaceScanner);
    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  public void testNoIssues() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example2"))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertNoIssues();
  }

  @Test
  public void testExcludingExactRootResultsInIssue() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
                    .add(DirectoryEntry.exclude(new WorkspacePath("java/com/google/android/apps/example"))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
      "java/com/google/android/apps/example is included, but that contradicts java/com/google/android/apps/example which was excluded");
  }

  @Test
  public void testExcludingRootViaParentResultsInIssue() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
                    .add(DirectoryEntry.exclude(new WorkspacePath("java/com/google/android/apps"))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
      "java/com/google/android/apps/example is included, but that contradicts java/com/google/android/apps which was excluded");
  }

  @Test
  public void testExcludingSubdirectoryOfRootResultsInNoIssues() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
                    .add(DirectoryEntry.exclude(new WorkspacePath("java/com/google/android/apps/example/subdir"))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertNoIssues();
  }

  @Test
  public void testImportRootMissingResultsInIssue() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example"))))
             .build())
      .build();
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
      String.format("Directory '%s' specified in import roots not found under workspace root '%s'",
                    "java/com/google/android/apps/example", "/root"));
  }

  @Test
  public void testOverlappingDirectoriesResultInIssue() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example"))))
             .build())
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android"))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
      "Overlapping directories: java/com/google/android/apps/example already included by java/com/google/android"
    );
  }

  @Test
  public void testRootDirectoryNotSpuriouslyOverlappingItself() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("."))))
             .build())
      .build();
    workspaceScanner.addProjectView(workspaceRoot, projectViewSet);
    ProjectViewVerifier.verifyProjectView(context, workspaceRoot, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertNoIssues();
  }
}
