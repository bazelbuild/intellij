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
package com.google.idea.blaze.javascript;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Sync integration tests for projects containing javascript. */
@RunWith(JUnit4.class)
public class JavascriptSyncTest extends BlazeSyncIntegrationTestCase {

  @Test
  public void testSimpleTestSourcesIdentified() {
    TestUtils.setPlatformPrefix(getTestRootDisposable(), PlatformUtils.IDEA_PREFIX);
    setProjectView(
        "directories:",
        "  common/jslayout/calendar",
        "  common/jslayout/tests",
        "targets:",
        "  //common/jslayout/...:all",
        "test_sources:",
        "  common/jslayout/tests",
        "workspace_type: javascript");

    VirtualFile rootFile = workspace.createDirectory(new WorkspacePath("common/jslayout/calendar"));

    VirtualFile testFile =
        workspace.createFile(new WorkspacePath("common/jslayout/tests/date_formatter_test.js"));

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL).addProjectViewTargets(true).build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    assertThat(getWorkspaceContentEntries()).hasSize(2);

    ContentEntry sourceEntry = findContentEntry(rootFile);
    assertThat(sourceEntry.getSourceFolders()).hasLength(1);

    SourceFolder nonTestSource = findSourceFolder(sourceEntry, rootFile);
    assertThat(nonTestSource.isTestSource()).isFalse();

    ContentEntry testEntry = findContentEntry(testFile.getParent());
    assertThat(testEntry.getSourceFolders()).hasLength(1);

    SourceFolder testSource = findSourceFolder(testEntry, testFile.getParent());
    assertThat(testSource.isTestSource()).isTrue();
  }

  @Test
  public void testTestSourcesMissingFromDirectoriesSectionAreAdded() {
    TestUtils.setPlatformPrefix(getTestRootDisposable(), PlatformUtils.IDEA_PREFIX);
    setProjectView(
        "directories:",
        "  common/jslayout",
        "targets:",
        "  //common/jslayout/...:all",
        "test_sources:",
        "  */tests",
        "workspace_type: javascript");

    VirtualFile testDir = workspace.createDirectory(new WorkspacePath("common/jslayout/tests"));

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL).addProjectViewTargets(true).build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(1);

    SourceFolder root = findSourceFolder(contentEntries.get(0), testDir.getParent());
    assertThat(root.isTestSource()).isFalse();

    SourceFolder testRoot = findSourceFolder(contentEntries.get(0), testDir);
    assertThat(testRoot).isNotNull();
    assertThat(testRoot.isTestSource()).isTrue();
  }

  @Test
  public void testTestSourceChildrenAreNotAddedAsSourceFolders() {
    TestUtils.setPlatformPrefix(getTestRootDisposable(), PlatformUtils.IDEA_PREFIX);
    // child directories of test sources are always test sources, so they should never
    // appear as separate SourceFolders.
    setProjectView(
        "directories:",
        "  common/jslayout",
        "targets:",
        "  //common/jslayout/...:all",
        "test_sources:",
        "  */tests/*",
        "workspace_type: javascript");

    VirtualFile rootDir = workspace.createDirectory(new WorkspacePath("common/jslayout"));
    VirtualFile nestedTestDir =
        workspace.createDirectory(new WorkspacePath("common/jslayout/tests/foo"));

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL).addProjectViewTargets(true).build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(1);

    SourceFolder root = findSourceFolder(contentEntries.get(0), rootDir);
    assertThat(root.isTestSource()).isFalse();

    SourceFolder child = findSourceFolder(contentEntries.get(0), nestedTestDir);
    assertThat(child).isNull();

    SourceFolder testRoot = findSourceFolder(contentEntries.get(0), nestedTestDir.getParent());
    assertThat(testRoot).isNotNull();
    assertThat(testRoot.isTestSource()).isTrue();
  }

  @Test
  public void testUsefulErrorMessageInCommunityEdition() {
    TestUtils.setPlatformPrefix(getTestRootDisposable(), PlatformUtils.IDEA_CE_PREFIX);
    setProjectView(
        "directories:",
        "  common/jslayout",
        "targets:",
        "  //common/jslayout/...:all",
        "workspace_type: javascript");

    workspace.createDirectory(new WorkspacePath("common/jslayout"));

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL).addProjectViewTargets(true).build();
    runBlazeSync(syncParams);
    errorCollector.assertIssues("IntelliJ Ultimate or CLion needed for JavaScript support.");
  }

  @Nullable
  private static SourceFolder findSourceFolder(ContentEntry entry, VirtualFile file) {
    for (SourceFolder sourceFolder : entry.getSourceFolders()) {
      if (file.equals(sourceFolder.getFile())) {
        return sourceFolder;
      }
    }
    return null;
  }
}
